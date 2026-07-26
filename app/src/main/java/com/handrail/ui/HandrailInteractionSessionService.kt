package com.handrail.ui

import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * The framework's factory for [HandrailInteractionSession] — bound and asked
 * for a fresh session every time the assist gesture fires. There is
 * deliberately no state here: everything the session needs (voice
 * preferences, the Sarvam clients) is constructed inside the session itself.
 */
class HandrailInteractionSessionService : VoiceInteractionSessionService() {

    override fun onNewSession(args: Bundle?): VoiceInteractionSession = HandrailInteractionSession(this)
}
