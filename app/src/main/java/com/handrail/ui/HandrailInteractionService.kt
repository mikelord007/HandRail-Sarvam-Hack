package com.handrail.ui

import android.service.voice.VoiceInteractionService
import android.util.Log

private const val TAG = "HandrailInteractionService"

/**
 * The system binds this (permission `BIND_VOICE_INTERACTION`, see the
 * manifest) once Handrail is chosen as the default assistant, and calls into
 * it whenever the assist gesture — long-press home, or the gesture-nav
 * corner swipe — fires. It exists only so the framework has a component to
 * resolve `sessionService` against (see res/xml/interaction_service.xml);
 * all real behaviour lives in [HandrailInteractionSession], created via
 * [HandrailInteractionSessionService].
 *
 * This is a second, separate entry point from [AssistActivity]'s
 * `android.intent.action.ASSIST` activity — that one still exists and still
 * works stand-alone (Settings' assistant chooser lists apps satisfying
 * either mechanism). Screen reading and tapping is the separate
 * [com.handrail.perception.HandrailAccessibilityService]; this service owns
 * only the voice session lifecycle.
 */
class HandrailInteractionService : VoiceInteractionService() {

    override fun onReady() {
        super.onReady()
        Log.i(TAG, "Handrail voice interaction service ready")
    }
}
