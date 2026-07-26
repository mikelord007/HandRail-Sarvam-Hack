package com.handrail.ui

/**
 * The screens [MainActivity] hosts. `assist` and `handback` are NOT here —
 * they live in [AssistActivity]'s own [OverlayScreen], a separate hierarchy,
 * because the two Activities can never navigate into each other and a
 * single 14-member sealed type would make every `when` non-exhaustive by
 * construction for whichever half you're not in.
 *
 * `ThreadDetail` is the one screen the design doesn't have: its History
 * "Conversations" tab rows have nowhere to go in the prototype (they just
 * navigate back to home). This is where a tapped conversation opens — see
 * the deviations table in the plan.
 */
sealed interface Screen {
    data object Welcome : Screen
    data object Language : Screen
    data object Voice : Screen
    data object MicPermission : Screen
    data object AccessibilityPermission : Screen
    data object AssistantPermission : Screen
    data object Promise : Screen
    data object Home : Screen
    data object History : Screen
    data class ThreadDetail(val chatId: String) : Screen
    data object Settings : Screen
}

/** [AssistActivity]'s own two-member hierarchy — see the class doc on [Screen]. */
sealed interface OverlayScreen {
    data object Assist : OverlayScreen
    data class Handback(
        val actionLabel: String,
        val spokenReason: String,
    ) : OverlayScreen
}
