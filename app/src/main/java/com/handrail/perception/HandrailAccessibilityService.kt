/*
 * Service lifecycle (singleton instance pattern, event wiring) adapted from
 * ClawAccessibilityService.kt in MobileClaw
 * (https://github.com/ChenKuanSun/MobileClaw), Copyright (c) 2026 CK Sun.
 * Licensed under the MIT License — see README.md for the full notice.
 */
package com.handrail.perception

import android.accessibilityservice.AccessibilityService
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.handrail.BuildConfig
import com.handrail.R
import com.handrail.actions.ActionExecutor
import com.handrail.actions.ActionResult
import com.handrail.actions.ScrollDirection
import com.handrail.ui.AgentGlowBorderView
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
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
    private var glowBorderView: AgentGlowBorderView? = null

    /** The currently-running Takeover job, if any — set via [trackAgentJob], cancelled via [stopActiveAgentTask]. */
    private var activeAgentJob: Job? = null

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
        createNotificationChannel()
        ContextCompat.registerReceiver(
            this,
            stopTaskReceiver,
            IntentFilter(ACTION_STOP_TASK),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
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
        unregisterReceiver(stopTaskReceiver)
        if (BuildConfig.DEBUG) {
            unregisterReceiver(debugActionReceiver)
        }
        hideAgentGlow()
        hideTaskNotification()
        serviceScope.cancel()
        instance = null
        super.onDestroy()
    }

    /**
     * Golden pulsing border shown while a Takeover task is actively running.
     * Added here rather than to AssistActivity's own window because that
     * window backgrounds every time the agent taps into another app — an
     * accessibility service can add a TYPE_ACCESSIBILITY_OVERLAY window
     * (no SYSTEM_ALERT_WINDOW permission needed) that survives those
     * switches, so it's the only place this cue can actually live. Callers
     * must be on the main thread — see AssistActivity's note on
     * [backgroundScope] dispatching on Dispatchers.Main.
     */
    fun showAgentGlow() {
        showTaskNotification()
        if (glowBorderView != null) return
        val view = AgentGlowBorderView(this)
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        try {
            windowManager.addView(view, params)
            glowBorderView = view
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add agent glow overlay", e)
        }
    }

    /** Stop condition (done/blocked/ask_user/error) or cancellation — see AssistActivity's onEvent handling and onStopRequested. */
    fun hideAgentGlow() {
        hideTaskNotification()
        val view = glowBorderView ?: return
        glowBorderView = null
        try {
            windowManager.removeView(view)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to remove agent glow overlay", e)
        }
    }

    /**
     * Registers the job driving the currently-running Takeover task so
     * [stopActiveAgentTask] — called from either the in-overlay Stop button
     * or this notification's Stop action — can cancel the right one. Must be
     * called once per task, right after launching it on [backgroundScope].
     */
    fun trackAgentJob(job: Job) {
        activeAgentJob = job
    }

    /**
     * Cancels whatever Takeover task is running. This is the ONLY way to
     * stop a task once a step has navigated into another app and
     * backgrounded AssistActivity — that's exactly when the notification's
     * Stop action (routed here via [stopTaskReceiver]) matters; the
     * in-overlay Stop button covers the case where the Activity is still
     * visible. Safe to call with nothing running.
     */
    fun stopActiveAgentTask() {
        activeAgentJob?.cancel()
        activeAgentJob = null
        hideAgentGlow()
    }

    private fun showTaskNotification() {
        val stopIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_STOP_TASK).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.notification_takeover_title))
            .setContentText(getString(R.string.notification_takeover_text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(0, getString(R.string.stop_label), stopIntent)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(TASK_NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted — the in-overlay Stop button still works whenever AssistActivity is visible.
            Log.w(TAG, "Notification permission not granted; task-stop notification not shown", e)
        }
    }

    private fun hideTaskNotification() {
        NotificationManagerCompat.from(this).cancel(TASK_NOTIFICATION_ID)
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_takeover_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    /** Handles the Stop action tapped on the task-running notification — the counterpart to AssistActivity's in-overlay Stop button for when the Activity is backgrounded. */
    private val stopTaskReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "Stop requested from notification")
            stopActiveAgentTask()
        }
    }

    private val windowManager: WindowManager
        get() = getSystemService(WINDOW_SERVICE) as WindowManager

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
            // retry a couple more times before giving up. Only press HOME
            // once: a stray CONTENT_CHANGED event unrelated to the launcher
            // (e.g. our own overlay closing) can satisfy awaitContentChanged
            // well under 1500ms while the launcher's root still isn't
            // queryable yet — re-pressing HOME on that retry was observed
            // interrupting the very transition still in flight from the
            // first press, burning all 3 retries in well under 200ms.
            Log.i(TAG, "captureScreen: no other app in the stack, going home first")
            performGlobalAction(GLOBAL_ACTION_HOME)
            repeat(3) { attempt ->
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
        private const val ACTION_STOP_TASK = "com.handrail.action.STOP_TASK"
        private const val NOTIFICATION_CHANNEL_ID = "takeover_task"
        private const val TASK_NOTIFICATION_ID = 1

        var instance: HandrailAccessibilityService? = null
            private set
    }
}
