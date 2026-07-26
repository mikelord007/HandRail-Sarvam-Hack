package com.handrail.ui

import android.content.Context

/**
 * The one first-run flag in the app. Set true only when the user reaches
 * the end of the promise screen (step 7 of 7) — skipping individual
 * permissions along the way is fine and never blocks this; see the
 * onboarding flow's "nothing dead-ends" rule.
 */
class OnboardingPreferences(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isComplete: Boolean
        get() = prefs.getBoolean(KEY_COMPLETE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_COMPLETE, value).apply()
        }

    private companion object {
        const val PREFS_NAME = "handrail_prefs"
        const val KEY_COMPLETE = "onboarding_complete"
    }
}
