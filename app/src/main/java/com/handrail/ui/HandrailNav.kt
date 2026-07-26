package com.handrail.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.mutableStateListOf

/**
 * A hand-rolled back stack for [MainActivity] — 11 screens, all linear or
 * single-level, don't need Navigation-Compose. [go] pushes; [back] pops;
 * [reset] clears the whole stack before jumping (used by promise -> home,
 * so system back from home exits the app instead of re-entering onboarding).
 */
class HandrailNav(start: Screen) {
    var current: Screen by mutableStateOf(start)
        private set

    private val stack: SnapshotStateList<Screen> = mutableStateListOf()

    val canGoBack: Boolean
        get() = stack.isNotEmpty()

    fun go(screen: Screen) {
        stack.add(current)
        current = screen
    }

    fun reset(screen: Screen) {
        stack.clear()
        current = screen
    }

    fun back() {
        current = stack.removeLastOrNull() ?: return
    }
}
