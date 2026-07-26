package com.handrail.ui

import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionSession
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.handrail.R
import com.handrail.actions.ActionExecutor
import com.handrail.agent.AgentEvent
import com.handrail.agent.AgentLoop
import com.handrail.perception.HandrailAccessibilityService
import com.handrail.sarvam.ChatMessage
import com.handrail.sarvam.LlmClient
import com.handrail.speech.AudioRecorder
import com.handrail.speech.BulbulClient
import com.handrail.speech.ErrorPhrases
import com.handrail.speech.NarrationPlayer
import com.handrail.speech.SaarasClient
import com.handrail.speech.VoicePreferences
import com.handrail.ui.screens.InvokeSheet
import com.handrail.ui.screens.InvokeState
import com.handrail.ui.theme.HandrailTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "HandrailInteractionSession"

/** The invoke sheet only ever shows this many visual segments — see [InvokeState.Working]'s doc. */
private const val VISUAL_SEGMENTS = 4

/** Must match [AgentLoop]'s own step budget so [segmentFor] groups correctly. */
private const val AGENT_STEP_BUDGET = 12

/**
 * The live session behind [InvokeSheet]: owns the Sarvam clients (Saaras STT,
 * Bulbul TTS, the 105B chat-completion client), the sheet's [InvokeState],
 * and the hand-off into the SAME [AgentLoop] machinery [AssistActivity] uses
 * for a takeover task, once the user confirms the spoken plan.
 *
 * A [VoiceInteractionSession] is not an Activity and provides none of the
 * scaffolding Compose needs to host a [ComposeView] outside one — this class
 * supplies [Lifecycle], [ViewModelStore] and [SavedStateRegistry] itself, the
 * standard recipe for embedding Compose in a raw window.
 *
 * Only three states exist on the sheet (see [InvokeState]); there is no
 * fourth "ask a follow-up" or "hard-stop card" state here. A follow-up
 * question from the agent loop ([AgentEvent.AskUser]) re-enters [InvokeState.Listening]
 * and resumes the same task once answered. A hard stop
 * ([AgentEvent.Blocked] with the irreversible-action guard already tripped —
 * see [com.handrail.actions.IrreversibleActionGuard]) narrates (inside
 * [AgentLoop] itself, before the event ever reaches here) and then dismisses
 * the sheet, which is itself the hand-back: the real screen and its real
 * button are visible and tappable again the instant this window closes.
 */
class HandrailInteractionSession(baseContext: Context) :
    VoiceInteractionSession(baseContext),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    // Deliberately NOT using `baseContext` below — every reference to
    // `context` in this class resolves to VoiceInteractionSession's own
    // inherited `getContext()`, which is the correct, live context for this
    // session (see that method's doc: it may wrap `baseContext`).

    private val lifecycleRegistry = LifecycleRegistry(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry

    private val store = ViewModelStore()
    override val viewModelStore: ViewModelStore get() = store

    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry

    private val player by lazy { NarrationPlayer(context) }
    private val llmClient = LlmClient()
    private val bulbulClient = BulbulClient()
    private val saarasClient = SaarasClient()
    private val audioRecorder by lazy { AudioRecorder(context) }
    private val voicePreferences by lazy { VoicePreferences(context) }

    /** Read fresh every use — the user may change language/speaker in Settings between invocations. */
    private val voice get() = voicePreferences.settings

    private var invokeState by mutableStateOf<InvokeState>(InvokeState.Listening)
    private var isRecording = false

    /** Held so Stop / back / swipe-down can actually cancel a running takeover, not just dismiss the UI. */
    private var agentJob: Job? = null

    /** The task + conversation-so-far to resume with once the user answers a pending ask_user, by voice. */
    private data class PendingAskUser(val task: String, val history: List<String>)
    private var pendingAskUser: PendingAskUser? = null

    init {
        // Must be called before onCreate() — see VoiceInteractionSession.setTheme's own doc — so it happens here, not in an onCreate override.
        setTheme(R.style.Theme_Handrail_InvokeSession)
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED
    }

    override fun onCreateContentView(): View {
        val view = ComposeView(context)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        view.setContent {
            HandrailTheme {
                InvokeSheet(
                    state = invokeState,
                    languageLabel = voice.language.displayName,
                    onMicTap = ::onMicTap,
                    onReadScreenAloud = ::onReadScreenAloud,
                    onDoTaskForMe = ::onDoTaskForMe,
                    onConfirmPlan = ::onConfirmPlan,
                    onRetryTranscript = ::onRetryTranscript,
                    onStop = ::dismissSession,
                    onSwipeDownDismiss = ::dismissSession,
                )
            }
        }
        return view
    }

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        // A stray touch on the app underneath must not itself dismiss Handrail
        // — the window is intentionally non-modal, not a cancel-on-outside-tap dialog.
        window.setCanceledOnTouchOutside(false)
        beginListening()
    }

    override fun onHide() {
        lifecycleRegistry.currentState = Lifecycle.State.STARTED
        super.onHide()
    }

    override fun onDestroy() {
        agentJob?.cancel()
        player.stop()
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            dismissSession()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    // --- Listening ---

    private fun beginListening() {
        pendingAskUser = null
        invokeState = InvokeState.Listening
        startRecording()
        // Spoken immediately, independent of the sheet's own ~220ms slide-in —
        // see InvokeSheet's doc: never wait on the animation to narrate.
        lifecycleScope.launch {
            val v = voice
            speak(context.getString(R.string.invoke_listening_title) + " " + context.getString(R.string.invoke_listening_prompt, v.language.displayName))
        }
    }

    private fun startRecording() {
        if (isRecording) return
        try {
            audioRecorder.start()
            isRecording = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
        }
    }

    /** Stops early without transcribing — used when a canned action (e.g. "Read this screen aloud") replaces the need to hear anything more. */
    private fun stopRecordingDiscarding() {
        if (!isRecording) return
        isRecording = false
        audioRecorder.stop()?.delete()
    }

    /** The mic orb's tap target: barge-in while the listening prompt is still speaking, or start listening again once idle. */
    private fun onMicTap() {
        if (invokeState !is InvokeState.Listening) return
        if (isRecording) {
            player.stop()
            stopRecordingAndTranscribe()
        } else {
            startRecording()
        }
    }

    private fun stopRecordingAndTranscribe() {
        isRecording = false
        val file = audioRecorder.stop() ?: return
        lifecycleScope.launch {
            val result = saarasClient.transcribe(file)
            file.delete()
            result.onSuccess { transcript ->
                if (transcript.isBlank()) return@onSuccess
                val pending = pendingAskUser
                if (pending != null) {
                    pendingAskUser = null
                    startTakeover(pending.task, pending.history + "User answered: $transcript")
                } else {
                    handleTranscript(transcript)
                }
            }.onFailure { error ->
                Log.e(TAG, "Saaras STT failed", error)
                speak(ErrorPhrases.couldNotDoThat(voice.language.code))
            }
        }
    }

    private suspend fun handleTranscript(transcript: String) {
        val plan = describePlan(transcript)
        invokeState = InvokeState.Heard(transcript = transcript, plan = plan)
        speak("${context.getString(R.string.invoke_heard_you)}. $plan ${context.getString(R.string.invoke_heard_promise)}")
    }

    private suspend fun describePlan(task: String): String {
        val v = voice
        val systemPrompt = PLAN_SYSTEM_PROMPT.replace("%LANGUAGE%", v.language.displayName)
        val result = llmClient.chatCompletion(
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = task),
            ),
        )
        val plan = result.getOrNull()?.choices?.firstOrNull()?.message?.content?.trim().orEmpty()
        return plan.ifBlank { task }
    }

    // --- Heard you ---

    /**
     * "Read this screen aloud" — reuses narration mode's own task text so
     * this session doesn't duplicate AssistActivity's perceive/summarize/
     * speak pipeline; it just starts a takeover with that fixed task.
     */
    private fun onReadScreenAloud() {
        stopRecordingDiscarding()
        val task = context.getString(R.string.read_the_screen_task)
        startTakeover(task)
    }

    /** No canned task text — the mic keeps listening for whatever the user says next. */
    private fun onDoTaskForMe() {
        stopRecordingDiscarding()
        startRecording()
    }

    private fun onRetryTranscript() {
        beginListening()
    }

    private fun onConfirmPlan() {
        val heard = invokeState as? InvokeState.Heard ?: return
        startTakeover(heard.transcript)
    }

    // --- Working (the agent loop) ---

    private fun startTakeover(task: String, initialHistory: List<String> = emptyList()) {
        val service = HandrailAccessibilityService.instance
        if (service == null) {
            lifecycleScope.launch { speak(context.getString(R.string.assist_a11y_not_enabled)) }
            dismissSession()
            return
        }
        invokeState = InvokeState.Working(step = 0, of = VISUAL_SEGMENTS, label = context.getString(R.string.step_reading_screen))

        // Runs in the accessibility service's own scope, not lifecycleScope:
        // a takeover routinely navigates into other apps, which would
        // background/hide this session and could cancel lifecycleScope —
        // see AssistActivity's identical reasoning for backgroundScope.
        agentJob = service.backgroundScope.launch {
            val executor = ActionExecutor(service)
            val loop = AgentLoop(service, executor, llmClient, bulbulClient, player, service.backgroundScope, voice)
            loop.run(task, initialHistory) { event -> handleAgentEvent(event, task) }
        }
    }

    private fun handleAgentEvent(event: AgentEvent, task: String) {
        when (event) {
            is AgentEvent.Step -> {
                invokeState = InvokeState.Working(
                    step = segmentFor(event.index),
                    of = VISUAL_SEGMENTS,
                    label = event.description.ifBlank { context.getString(R.string.step_label_format, event.index) },
                )
            }
            // Done/Blocked/Error have all already narrated inside AgentLoop
            // itself before this callback runs (see AgentLoop.run) — the
            // irreversible-action guard's hard stop is enforced by CODE
            // there (IrreversibleActionGuard, checked by ActionExecutor
            // before any tap), not by anything in this UI layer. All three
            // just dismiss, which hands the real screen back.
            is AgentEvent.Done -> dismissSession()
            is AgentEvent.Blocked -> dismissSession()
            is AgentEvent.Error -> dismissSession()
            is AgentEvent.AskUser -> {
                pendingAskUser = PendingAskUser(task, event.history)
                beginListening()
            }
        }
    }

    /** Collapses the agent loop's real step budget down to this sheet's four visible segments. */
    private fun segmentFor(stepIndex: Int): Int =
        (((stepIndex - 1) * VISUAL_SEGMENTS) / AGENT_STEP_BUDGET).coerceIn(0, VISUAL_SEGMENTS - 1) + 1

    // --- Stop / dismiss ---

    /** Stop button, back, swipe-down, and a completed/blocked/errored takeover all end here. */
    private fun dismissSession() {
        agentJob?.cancel()
        player.stop()
        stopRecordingDiscarding()
        finish()
    }

    private suspend fun speak(text: String) {
        if (text.isBlank()) return
        val v = voice
        player.playStream(bulbulClient.httpClient, bulbulClient.streamRequest(text, v.language.code, v.speaker.id, v.pace))
    }

    private companion object {
        val PLAN_SYSTEM_PROMPT = """
            You are Handrail. The user just spoke a task in %LANGUAGE%. In one short spoken sentence in %LANGUAGE%, describe what you are about to do to accomplish it — do not perform anything yet, only describe the plan. No markdown, no lists, no element references.
        """.trimIndent()
    }
}
