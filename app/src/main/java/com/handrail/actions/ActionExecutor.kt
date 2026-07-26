package com.handrail.actions

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Path
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.handrail.perception.HandrailAccessibilityService
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

sealed interface ActionResult {
    data object Success : ActionResult

    /**
     * [label] is the blocked node's own visible text (e.g. "Pay ₹450") —
     * carried through so the hand-back UI can show the real button instead
     * of a generic message. [matchedKeyword] is the guard keyword that
     * tripped, kept for logging/history only.
     */
    data class Blocked(val label: String, val matchedKeyword: String) : ActionResult
    data class Failed(val reason: String) : ActionResult
}

enum class ScrollDirection { UP, DOWN }

private const val TAG = "ActionExecutor"
private const val SETTLE_TIMEOUT_MS = 1500L
private const val TAP_DURATION_MS = 50L

class ActionExecutor(private val service: HandrailAccessibilityService) {

    suspend fun tap(ref: String, refMap: Map<String, AccessibilityNodeInfo>): ActionResult {
        val node = refMap[ref] ?: return ActionResult.Failed("Unknown ref: $ref")

        val nodeText = collectDescendantText(node)
        val matchedKeyword = IrreversibleActionGuard.matchedKeyword(nodeText)
        if (matchedKeyword != null) {
            Log.w(TAG, "tap($ref) blocked: matched \"$matchedKeyword\" in \"$nodeText\"")
            return ActionResult.Blocked(label = nodeText, matchedKeyword = matchedKeyword)
        }

        val result = if (performClickWalkingUpToClickableAncestor(node)) {
            ActionResult.Success
        } else {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            // A node that isn't currently laid out (e.g. scrolled off-screen)
            // can report stale/degenerate bounds — never dispatch a gesture
            // at a nonsensical or inverted rect.
            if (bounds.isEmpty || bounds.top >= bounds.bottom || bounds.left >= bounds.right) {
                Log.w(TAG, "tap($ref) ACTION_CLICK failed and bounds are invalid ($bounds) — skipping coordinate fallback")
                ActionResult.Failed("tap($ref) has no valid on-screen location; it may need scrolling into view first")
            } else {
                Log.w(TAG, "tap($ref) ACTION_CLICK failed on node and ancestors, falling back to coordinate tap at $bounds")
                if (dispatchTap(bounds.centerX(), bounds.centerY())) {
                    ActionResult.Success
                } else {
                    ActionResult.Failed("tap($ref) failed via both performAction and coordinate fallback")
                }
            }
        }

        service.awaitContentChanged(SETTLE_TIMEOUT_MS)
        return result
    }

    suspend fun setText(ref: String, text: String, refMap: Map<String, AccessibilityNodeInfo>): ActionResult {
        val node = refMap[ref] ?: return ActionResult.Failed("Unknown ref: $ref")

        setTextDirect(node, text)
        if (readsBack(node, text)) {
            service.awaitContentChanged(SETTLE_TIMEOUT_MS)
            return ActionResult.Success
        }

        Log.w(TAG, "setText($ref) via ACTION_SET_TEXT didn't take, falling back to clipboard paste")
        node.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        pasteViaClipboard(text)
        node.performAction(AccessibilityNodeInfo.ACTION_PASTE)

        val result = if (readsBack(node, text)) {
            ActionResult.Success
        } else {
            Log.e(TAG, "setText($ref) did not take, even after clipboard fallback")
            ActionResult.Failed("setText($ref) did not take, even after clipboard fallback")
        }

        service.awaitContentChanged(SETTLE_TIMEOUT_MS)
        return result
    }

    suspend fun scroll(direction: ScrollDirection, refMap: Map<String, AccessibilityNodeInfo>): ActionResult {
        val node = pickScrollTarget(refMap)
            ?: return ActionResult.Failed("No scrollable element on screen")

        val action = if (direction == ScrollDirection.DOWN) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val result = if (node.performAction(action)) {
            ActionResult.Success
        } else {
            ActionResult.Failed("scroll($direction) failed")
        }
        service.awaitContentChanged(SETTLE_TIMEOUT_MS)
        return result
    }

    suspend fun back(): ActionResult {
        val result = if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) {
            ActionResult.Success
        } else {
            ActionResult.Failed("back() failed")
        }
        service.awaitContentChanged(SETTLE_TIMEOUT_MS)
        return result
    }

    suspend fun home(): ActionResult {
        val result = if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)) {
            ActionResult.Success
        } else {
            ActionResult.Failed("home() failed")
        }
        service.awaitContentChanged(SETTLE_TIMEOUT_MS)
        return result
    }

    /**
     * The model sometimes targets a label ref instead of the clickable row
     * wrapping it (e.g. the TextView instead of its parent LinearLayout).
     * Try the node itself, then walk up ancestors for the nearest clickable
     * one, before ever falling back to a raw coordinate tap.
     */
    private fun performClickWalkingUpToClickableAncestor(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        while (current != null) {
            if (current.isClickable && current.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                return true
            }
            current = current.parent
        }
        return false
    }

    /**
     * Outer wrapper ScrollViews (collapsing headers, NestedScrollView
     * containers) often report isScrollable=true with a larger footprint
     * than the actual content list nested inside them. Prefer whichever
     * scrollable candidate sits inside another one — that's the real list.
     */
    private fun pickScrollTarget(refMap: Map<String, AccessibilityNodeInfo>): AccessibilityNodeInfo? {
        val scrollables = refMap.values.filter { it.isScrollable }
        if (scrollables.size <= 1) return scrollables.firstOrNull()

        return scrollables.firstOrNull { candidate ->
            scrollables.any { other -> other != candidate && isAncestorOf(other, candidate) }
        } ?: scrollables.first()
    }

    private fun isAncestorOf(potentialAncestor: AccessibilityNodeInfo, node: AccessibilityNodeInfo): Boolean {
        var current = node.parent
        while (current != null) {
            if (current == potentialAncestor) return true
            current = current.parent
        }
        return false
    }

    private fun setTextDirect(node: AccessibilityNodeInfo, text: String) {
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun readsBack(node: AccessibilityNodeInfo, expected: String): Boolean {
        node.refresh()
        return node.text?.toString()?.trim() == expected.trim()
    }

    private fun pasteViaClipboard(text: String) {
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("handrail", text))
    }

    private suspend fun dispatchTap(x: Int, y: Int): Boolean = suspendCancellableCoroutine { cont ->
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION_MS))
            .build()
        val dispatched = service.dispatchGesture(
            gesture,
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(true)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) {
                    if (cont.isActive) cont.resume(false)
                }
            },
            null,
        )
        if (!dispatched && cont.isActive) cont.resume(false)
    }

    private fun collectDescendantText(node: AccessibilityNodeInfo, maxDepth: Int = 3): String {
        val parts = mutableListOf<String>()

        fun visit(current: AccessibilityNodeInfo, depth: Int) {
            current.text?.toString()?.let { parts.add(it) }
            current.contentDescription?.toString()?.let { parts.add(it) }
            if (depth >= maxDepth) return
            for (i in 0 until current.childCount) {
                val child = current.getChild(i) ?: continue
                visit(child, depth + 1)
            }
        }

        visit(node, 0)
        return parts.joinToString(" ")
    }
}
