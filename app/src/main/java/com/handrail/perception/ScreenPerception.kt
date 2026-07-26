/*
 * Tree-walk structure adapted from ClawAccessibilityService.kt in MobileClaw
 * (https://github.com/ChenKuanSun/MobileClaw), Copyright (c) 2026 CK Sun.
 * Licensed under the MIT License — see README.md for the full notice.
 */
package com.handrail.perception

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import kotlin.math.max

data class PerceptionResult(
    val serialized: String,
    val refMap: Map<String, AccessibilityNodeInfo>,
)

private const val NODE_BUDGET = 150

// A single unclipped label (e.g. a full news headline) can run past 150
// characters; on a busy screen with dozens of these, the serialized prompt
// balloons well past what CLAUDE.md's "~150 nodes" budget was meant to cap,
// and was observed making the LLM call stall indefinitely on a Google News
// results page. Truncate every label, not just on over-budget screens — the
// ref + bounds are what the model acts on, the label just needs to be
// enough to identify the element.
private const val MAX_LABEL_LENGTH = 80

object ScreenPerception {

    fun capture(root: AccessibilityNodeInfo, screenWidth: Int, screenHeight: Int): PerceptionResult {
        val candidates = mutableListOf<Candidate>()
        walk(root, depth = 0, screenWidth, screenHeight, candidates)

        val selected = applyBudget(candidates)

        val refMap = LinkedHashMap<String, AccessibilityNodeInfo>()
        val lines = StringBuilder()
        selected.forEachIndexed { index, candidate ->
            val ref = "e${index + 1}"
            refMap[ref] = candidate.node
            lines.appendLine(formatLine(ref, candidate))
        }

        return PerceptionResult(serialized = lines.toString().trimEnd(), refMap = refMap)
    }

    private class Candidate(
        val node: AccessibilityNodeInfo,
        val depth: Int,
        val className: String,
        val label: String?,
        val clickable: Boolean,
        val editable: Boolean,
        val scrollable: Boolean,
        val checkable: Boolean,
        val onScreen: Boolean,
        val boundsCenter: Pair<Int, Int>?,
    ) {
        val actionable: Boolean get() = clickable || editable || scrollable || checkable
    }

    private fun walk(
        node: AccessibilityNodeInfo,
        depth: Int,
        screenWidth: Int,
        screenHeight: Int,
        out: MutableList<Candidate>,
    ) {
        val text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val contentDesc = node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val label = text ?: contentDesc

        val clickable = node.isClickable
        val editable = node.isEditable
        val scrollable = node.isScrollable
        val checkable = node.isCheckable

        val keep = clickable || editable || scrollable || checkable || label != null

        if (keep) {
            val bounds = Rect()
            node.getBoundsInScreen(bounds)
            val onScreen = !bounds.isEmpty && bounds.right > 0 && bounds.bottom > 0 &&
                bounds.left < screenWidth && bounds.top < screenHeight && bounds.top < bounds.bottom
            val actionable = clickable || editable || scrollable || checkable
            // Off-screen nodes (e.g. below the fold in a scrollable list) can
            // report stale or degenerate bounds since they aren't currently
            // laid out — never hand those out as a tap target. A node
            // without bounds signals "exists, but scroll to reach it".
            val boundsCenter = if (actionable && onScreen) Pair(bounds.centerX(), bounds.centerY()) else null

            out.add(
                Candidate(
                    node = node,
                    depth = depth,
                    className = node.className?.toString()?.substringAfterLast('.') ?: "View",
                    label = label,
                    clickable = clickable,
                    editable = editable,
                    scrollable = scrollable,
                    checkable = checkable,
                    onScreen = onScreen,
                    boundsCenter = boundsCenter,
                ),
            )
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            walk(child, depth + 1, screenWidth, screenHeight, out)
        }
    }

    private fun applyBudget(candidates: List<Candidate>): List<Candidate> {
        if (candidates.size <= NODE_BUDGET) return candidates

        val actionable = candidates.filter { it.actionable }
            .sortedWith(compareByDescending<Candidate> { it.onScreen }.thenBy { it.depth })
        val textOnly = candidates.filter { !it.actionable }
            .sortedWith(compareByDescending<Candidate> { it.onScreen }.thenBy { it.depth })

        val keptActionable = actionable.take(NODE_BUDGET)
        val remaining = max(0, NODE_BUDGET - keptActionable.size)
        val keptText = textOnly.take(remaining)

        val keptSet = (keptActionable + keptText).toHashSet()
        return candidates.filter { it in keptSet }
    }

    private fun formatLine(ref: String, candidate: Candidate): String {
        val sb = StringBuilder()
        sb.append(ref).append(' ').append(candidate.className)
        candidate.label?.let { sb.append(" \"").append(truncateLabel(it)).append('"') }

        val flags = buildList {
            if (candidate.clickable) add("clickable")
            if (candidate.editable) add("editable")
            if (candidate.scrollable) add("scrollable")
            if (candidate.checkable) add("checkable")
        }
        if (flags.isNotEmpty()) sb.append(' ').append(flags.joinToString(" "))

        candidate.boundsCenter?.let { (x, y) -> sb.append(" bounds=[$x,$y]") }
        return sb.toString()
    }

    private fun truncateLabel(label: String): String {
        if (label.length <= MAX_LABEL_LENGTH) return label
        return label.take(MAX_LABEL_LENGTH - 1).trimEnd() + "…"
    }
}
