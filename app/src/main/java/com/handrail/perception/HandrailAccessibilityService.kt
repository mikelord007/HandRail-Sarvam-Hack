/*
 * Service lifecycle (singleton instance pattern, event wiring) adapted from
 * ClawAccessibilityService.kt in MobileClaw
 * (https://github.com/ChenKuanSun/MobileClaw), Copyright (c) 2026 CK Sun.
 * Licensed under the MIT License — see README.md for the full notice.
 */
package com.handrail.perception

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.content.ContextCompat
import com.handrail.BuildConfig
import com.handrail.actions.ActionExecutor
import com.handrail.actions.ActionResult
import com.handrail.actions.ScrollDirection
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

class HandrailAccessibilityService : AccessibilityService() {

    private var lastPerception: PerceptionResult? = null
    private val contentChangeWaiters = mutableListOf<CompletableDeferred<Unit>>()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val perceptionMutex = Mutex()

    /**
     * A CoroutineScope that outlives any particular Activity. The agent loop
     * must run here, not in an Activity's lifecycleScope — Takeover tasks
     * routinely navigate to OTHER apps' screens, which backgrounds our own
     * Activity constantly by design, and lifecycleScope cancels the moment
     * that happens. Was observed silently killing multi-step tasks mid-run.
     */
    val backgroundScope: CoroutineScope
        get() = serviceScope

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "Handrail accessibility service connected")
        if (BuildConfig.DEBUG) {
            ContextCompat.registerReceiver(
                this,
                debugActionReceiver,
                IntentFilter(DEBUG_ACTION_INTENT),
                ContextCompat.RECEIVER_EXPORTED,
            )
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // Only signal the settle-wait waiters here — do NOT also run our own
        // captureScreen() on every event. An extra concurrent perception
        // pass shares Android's accessibility node cache with whatever pass
        // a caller (e.g. the agent loop) is actively mid-use of, and was
        // observed invalidating those AccessibilityNodeInfo references,
        // breaking ACTION_CLICK on perfectly valid, freshly-captured nodes.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            notifyContentChanged()
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Handrail accessibility service interrupted")
    }

    override fun onDestroy() {
        if (BuildConfig.DEBUG) {
            unregisterReceiver(debugActionReceiver)
        }
        serviceScope.cancel()
        instance = null
        super.onDestroy()
    }

    /**
     * Serialized via a mutex: two overlapping captureScreen() calls (e.g.
     * narration mode and the agent loop both perceiving around the same
     * moment) share Android's accessibility node cache, and were observed
     * invalidating each other's AccessibilityNodeInfo references — breaking
     * ACTION_CLICK on nodes that were valid moments earlier.
     */
    /**
     * @param goHomeIfEmpty Only true for the FIRST perception of a fresh
     * invocation (a brand-new narration or the first step of a new agent
     * task), where nothing else may be in the window stack yet. Forcing
     * GLOBAL_ACTION_HOME on every empty read — including the transient gaps
     * mid-task while an app we just navigated to is still loading — was
     * observed undoing the agent's own navigation: it would tap into an app,
     * see a momentary empty window during the transition, and get shoved
     * back to the home screen before the app finished loading.
     */
    suspend fun captureScreen(goHomeIfEmpty: Boolean = false): PerceptionResult? = perceptionMutex.withLock {
        val root = resolveRoot(goHomeIfEmpty) ?: run {
            Log.w(TAG, "captureScreen: no active window found")
            return@withLock null
        }
        val metrics = resources.displayMetrics
        val result = ScreenPerception.capture(root, metrics.widthPixels, metrics.heightPixels)
        Log.i(TAG, "Perceived ${result.refMap.size} elements:\n${result.serialized}")
        lastPerception = result
        result
    }

    private suspend fun resolveRoot(goHomeIfEmpty: Boolean): AccessibilityNodeInfo? {
        resolveTargetRoot()?.let { return it }
        if (goHomeIfEmpty) {
            // Nothing else is in the window stack — e.g. Handrail was opened
            // fresh with nothing else running. Without this, the only window
            // left to perceive is our own translucent overlay. Go home first
            // so there's something real to act on. GLOBAL_ACTION_HOME + the
            // launcher window actually attaching a root node was observed
            // occasionally taking longer than one 1500ms wait under load —
            // retry a couple more times before giving up.
            Log.i(TAG, "captureScreen: no other app in the stack, going home first")
            repeat(3) { attempt ->
                performGlobalAction(GLOBAL_ACTION_HOME)
                awaitContentChanged(1500L)
                resolveTargetRoot()?.let { return it }
                Log.w(TAG, "captureScreen: still no active window after going home (attempt ${attempt + 1})")
            }
        } else {
            // Mid-task transient gap, most likely the app we just navigated
            // to is still loading. Wait it out — do NOT go home here, that
            // would cancel whatever the agent just successfully navigated to.
            repeat(2) {
                awaitContentChanged(800L)
                resolveTargetRoot()?.let { return it }
            }
        }
        return null
    }

    /**
     * Suspends until a TYPE_WINDOW_CONTENT_CHANGED event fires or the
     * timeout elapses, whichever first — callers must not re-perceive
     * immediately after an action, or they'll read the pre-action screen.
     */
    suspend fun awaitContentChanged(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs) {
            val deferred = CompletableDeferred<Unit>()
            contentChangeWaiters.add(deferred)
            deferred.await()
        }
    }

    private fun notifyContentChanged() {
        if (contentChangeWaiters.isEmpty()) return
        val waiters = contentChangeWaiters.toList()
        contentChangeWaiters.clear()
        waiters.forEach { it.complete(Unit) }
    }

    /**
     * Handrail's own ASSIST activity is translucent and sits on top of the
     * app being narrated. rootInActiveWindow tracks whichever window last
     * had focus, which is often our own overlay rather than the app
     * underneath — so prefer the topmost application window that isn't us.
     * Deliberately does NOT fall back to rootInActiveWindow: that can be our
     * own window too, and a caller needs to know when nothing else exists.
     */
    private fun resolveTargetRoot(): AccessibilityNodeInfo? {
        val target = windows
            ?.filter { window ->
                window.type == AccessibilityWindowInfo.TYPE_APPLICATION &&
                    window.root?.packageName?.toString() != packageName
            }
            ?.maxByOrNull { it.layer }
        return target?.root
    }

    /**
     * Debug-only hook (never registered in release builds) so the action
     * executor can be exercised on-device via `adb shell am broadcast`
     * before the agent loop exists to drive it.
     */
    private val debugActionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val type = intent.getStringExtra("type") ?: return
            val ref = intent.getStringExtra("ref")
            val text = intent.getStringExtra("text")
            val direction = intent.getStringExtra("direction")
            val refMap = lastPerception?.refMap ?: emptyMap()
            val executor = ActionExecutor(this@HandrailAccessibilityService)

            serviceScope.launch {
                val result = when (type) {
                    "tap" -> ref?.let { executor.tap(it, refMap) }
                        ?: ActionResult.Failed("missing ref")
                    "setText" -> if (ref != null && text != null) {
                        executor.setText(ref, text, refMap)
                    } else {
                        ActionResult.Failed("missing ref/text")
                    }
                    "scroll" -> executor.scroll(
                        if (direction == "up") ScrollDirection.UP else ScrollDirection.DOWN,
                        refMap,
                    )
                    "back" -> executor.back()
                    "home" -> executor.home()
                    else -> ActionResult.Failed("unknown type: $type")
                }
                Log.i(TAG, "DEBUG_ACTION type=$type ref=$ref text=$text -> $result")
            }
        }
    }

    companion object {
        private const val TAG = "HandrailA11yService"
        private const val DEBUG_ACTION_INTENT = "com.handrail.debug.ACTION"

        var instance: HandrailAccessibilityService? = null
            private set
    }
}
