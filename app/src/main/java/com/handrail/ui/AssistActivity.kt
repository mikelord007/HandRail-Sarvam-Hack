package com.handrail.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.annotation.StringRes
import androidx.lifecycle.lifecycleScope
import com.handrail.R
import com.handrail.actions.ActionExecutor
import com.handrail.agent.AgentEvent
import com.handrail.agent.AgentLoop
import com.handrail.agent.BlockCause
import com.handrail.chat.ChatEntry
import com.handrail.chat.ChatHistoryStore
import com.handrail.chat.ChatStatus
import com.handrail.chat.ChatTurn
import com.handrail.chat.EntryKind
import com.handrail.perception.HandrailAccessibilityService
import com.handrail.sarvam.ChatMessage
import com.handrail.sarvam.LlmClient
import com.handrail.speech.AudioRecorder
import com.handrail.speech.BulbulClient
import com.handrail.speech.ErrorPhrases
import com.handrail.speech.NarrationPlayer
import com.handrail.speech.SaarasClient
import com.handrail.speech.SupportedLanguages
import com.handrail.speech.VoicePreferences
import com.handrail.speech.VoiceSettings
import com.handrail.ui.components.VoiceState
import com.handrail.ui.screens.AssistOverlayScreen
import com.handrail.ui.screens.HandbackScreen
import com.handrail.ui.theme.HandrailTheme
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private const val TAG = "AssistActivity"

private sealed interface SttState {
    data object Idle : SttState
    data object Recording : SttState
    data object Transcribing : SttState
    data class Error(val message: String) : SttState
}

/** The task + conversation-so-far to resume with once the user answers a pending ask_user. */
private data class PendingAgentContext(val task: String, val history: List<String>, val chatId: String)

/**
 * The translucent overlay — narration mode and the takeover loop share it.
 * Window starts as a bottom sheet ([applyWindowMode] false): translucent,
 * `FLAG_NOT_TOUCH_MODAL` so coordinate-tap fallback still reaches the app
 * underneath while the agent is acting. It becomes a full-screen modal only
 * for the hand-back hard stop — by then [AgentLoop.run] has already
 * returned, so modality there is safe (nothing is pending) and desirable
 * (a stray touch must not land on a live payment screen).
 */
class AssistActivity : ComponentActivity() {

    private val player by lazy { NarrationPlayer(applicationContext) }
    private val llmClient = LlmClient()
    private val bulbulClient = BulbulClient()
    private val saarasClient = SaarasClient()
    private val audioRecorder by lazy { AudioRecorder(applicationContext) }
    private val voicePreferences by lazy { VoicePreferences(applicationContext) }
    private val chatHistoryStore by lazy { ChatHistoryStore(applicationContext) }

    /** Read fresh every use — the user may change language/speaker/pace in Settings between invocations. */
    private val voice: VoiceSettings
        get() = voicePreferences.settings

    private var overlayScreen by mutableStateOf<OverlayScreen>(OverlayScreen.Assist)
    /** Re-read every onResume (see that override's note) so a language changed in Settings shows up even though this Activity instance never recreates. */
    private var currentLanguageCode by mutableStateOf(SupportedLanguages.DEFAULT.code)
    private var voiceState by mutableStateOf(VoiceState.Idle)
    private var narrationLine by mutableStateOf("")
    private var stepLabel by mutableStateOf("")
    private var sttState by mutableStateOf<SttState>(SttState.Idle)
    private var pendingAgentContext by mutableStateOf<PendingAgentContext?>(null)

    /** Held so Stop/×/"No, stop" can actually cancel a running takeover, not just dismiss the UI. */
    private var agentJob: Job? = null

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startRecording()
        } else {
            // Log/internal only — SttState.Error's message is never rendered; the user hears ErrorPhrases via speakErrorAsync().
            sttState = SttState.Error("Microphone permission denied")
            speakErrorAsync()
        }
    }

    /** Sets the app's display language from the very first frame — see [LocalizedContent] for the live update applied every [onResume]. */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase.withLocale(VoicePreferences(newBase).language.code))
    }

    /** Resolves a string against the CURRENT preference, not this Activity's (possibly stale, singleTask-reused) base locale — see [currentLanguageCode]. */
    private fun str(@StringRes id: Int, vararg args: Any): String =
        applicationContext.withLocale(voice.language.code).getString(id, *args)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyWindowMode(modal = false)
        currentLanguageCode = voicePreferences.language.code

        setContent {
            HandrailTheme {
                LocalizedContent(languageCode = currentLanguageCode) {
                BackHandler { onStopRequested() }

                when (val screen = overlayScreen) {
                    OverlayScreen.Assist -> AssistOverlayScreen(
                        voiceState = voiceState,
                        narrationLine = narrationLine,
                        stepLabel = stepLabel,
                        onClose = ::onStopRequested,
                        onStop = ::onStopRequested,
                        onTapToAnswer = if (pendingAgentContext != null) ::onMicToggle else null,
                    )
                    is OverlayScreen.Handback -> HandbackScreen(
                        actionLabel = screen.actionLabel,
                        onConfirm = ::onHandbackConfirm,
                        onDecline = ::onStopRequested,
                    )
                }
                }
            }
        }
        runNarrationOrIncomingAgentTask(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        runNarrationOrIncomingAgentTask(intent)
    }

    override fun onResume() {
        super.onResume()
        // singleTask means this Activity backgrounds/resumes constantly
        // during a takeover (every step navigates into another app) —
        // re-assert idempotently from current state rather than assuming
        // onCreate's flags survived.
        applyWindowMode(modal = overlayScreen is OverlayScreen.Handback)
        currentLanguageCode = voicePreferences.language.code
    }

    /**
     * The only place window geometry is touched. `modal = true` (hand-back):
     * full-screen, focusable, touch-modal — a stray touch must not leak to
     * whatever's underneath. `modal = false` (assist): bottom sheet,
     * FLAG_NOT_TOUCH_MODAL so the agent's coordinate-tap fallback still
     * reaches the app underneath.
     */
    private fun applyWindowMode(modal: Boolean) {
        if (modal) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            )
            window.setGravity(Gravity.BOTTOM)
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    /**
     * Narration and the agent loop are mutually exclusive per invocation —
     * both hit the accessibility service's single captureScreen()/
     * lastPerception state, and running them concurrently was observed
     * corrupting each other's perception passes. A typed/spoken task from
     * MainActivity means Takeover; a bare ASSIST invoke with no task means
     * Narration — and if a takeover is already running, a stray bare invoke
     * (e.g. long-press home again mid-task) is ignored rather than starting
     * a second, corrupting perception pass.
     */
    private fun runNarrationOrIncomingAgentTask(intent: Intent) {
        val task = intent.getStringExtra(EXTRA_AGENT_TASK)
        if (task == null) {
            if (agentJob?.isActive == true) {
                Log.i(TAG, "Ignoring a bare narration invoke while a takeover is running")
                return
            }
            runNarration()
            return
        }
        val chatId = intent.getStringExtra(EXTRA_CHAT_ID)
        val history = intent.getStringArrayListExtra(EXTRA_AGENT_HISTORY) ?: arrayListOf()
        runAgentLoop(task, chatId, history)
    }

    // --- Narration mode ---

    private fun runNarration() {
        lifecycleScope.launch {
            overlayScreen = OverlayScreen.Assist
            voiceState = VoiceState.Thinking
            narrationLine = ""
            stepLabel = str(R.string.step_reading_screen)

            val service = HandrailAccessibilityService.instance
            if (service == null) {
                val message = str(R.string.assist_a11y_not_enabled)
                Log.e(TAG, "Accessibility service is not enabled.")
                showIdleMessage(message)
                speakError()
                return@launch
            }

            val perception = service.captureScreen(goHomeIfEmpty = true)
            if (perception == null || perception.refMap.isEmpty()) {
                val message = str(R.string.assist_nothing_readable)
                Log.w(TAG, "Nothing readable on screen right now.")
                showIdleMessage(message)
                speakError()
                return@launch
            }

            val summary = summarize(perception.serialized)
            if (summary.isFailure) {
                val message = str(R.string.assist_couldnt_reach_llm)
                Log.e(TAG, "Couldn't reach the language model.", summary.exceptionOrNull())
                showIdleMessage(message)
                speakError()
                return@launch
            }
            val narrationText = summary.getOrThrow()

            voiceState = VoiceState.Speaking
            narrationLine = narrationText
            stepLabel = ""
            val v = voice
            val audio = player.playStream(bulbulClient.httpClient, bulbulClient.streamRequest(narrationText, v.language.code, v.speaker.id, v.pace))
            if (audio.isFailure) {
                // The narration channel itself is what failed — nothing left to narrate with.
                Log.e(TAG, "Couldn't speak the narration.", audio.exceptionOrNull())
                voiceState = VoiceState.Idle
                return@launch
            }

            voiceState = VoiceState.Idle
            // A narration entry so History isn't empty on a demo that never runs a takeover task.
            chatHistoryStore.upsert(
                ChatEntry(
                    id = UUID.randomUUID().toString(),
                    task = str(R.string.read_the_screen_task),
                    timestamp = System.currentTimeMillis(),
                    turns = listOf(ChatTurn("assistant", narrationText)),
                    status = ChatStatus.DONE,
                    kind = EntryKind.NARRATION,
                ),
            )
        }
    }

    private fun showIdleMessage(message: String) {
        voiceState = VoiceState.Idle
        narrationLine = message
        stepLabel = ""
    }

    private suspend fun summarize(perceivedScreen: String): Result<String> {
        val systemPrompt = NARRATION_SYSTEM_PROMPT.replace("%LANGUAGE%", voice.language.displayName)
        val result = llmClient.chatCompletion(
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = perceivedScreen),
            ),
        )
        return result.map { response ->
            response.choices.firstOrNull()?.message?.content?.trim().orEmpty()
        }.mapCatching { text ->
            if (text.isEmpty()) throw IllegalStateException("Empty narration from LLM") else text
        }
    }

    // --- STT capture (only used here to answer a pending ask_user by voice — the design has no other in-overlay mic) ---

    private fun onMicToggle() {
        if (sttState is SttState.Recording) {
            stopRecordingAndTranscribe()
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            startRecording()
        } else {
            requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        try {
            audioRecorder.start()
            sttState = SttState.Recording
            voiceState = VoiceState.Listening
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
            sttState = SttState.Error("Couldn't start the microphone")
            speakErrorAsync()
        }
    }

    private fun stopRecordingAndTranscribe() {
        val file = audioRecorder.stop()
        if (file == null) {
            sttState = SttState.Error("No audio captured")
            speakErrorAsync()
            return
        }
        sttState = SttState.Transcribing
        voiceState = VoiceState.Thinking
        lifecycleScope.launch {
            val result = saarasClient.transcribe(file)
            file.delete()
            result.onSuccess { transcript ->
                Log.i(TAG, "Saaras transcript: $transcript")
                sttState = SttState.Idle
                val pending = pendingAgentContext
                if (pending != null) {
                    pendingAgentContext = null
                    runAgentLoop(pending.task, pending.chatId, pending.history + "User answered: $transcript")
                } else {
                    runAgentLoop(transcript, chatId = null, initialHistory = emptyList())
                }
            }.onFailure { error ->
                Log.e(TAG, "Saaras STT failed", error)
                sttState = SttState.Error("Couldn't transcribe: ${error.message}")
                speakError()
            }
        }
    }

    // --- Takeover mode (agent loop) ---

    private fun runAgentLoop(task: String, chatId: String?, initialHistory: List<String>) {
        overlayScreen = OverlayScreen.Assist
        applyWindowMode(modal = false)
        voiceState = VoiceState.Thinking
        narrationLine = ""
        stepLabel = str(R.string.step_reading_screen)

        val resolvedChatId = chatId ?: UUID.randomUUID().toString()
        if (chatHistoryStore.find(resolvedChatId) == null) {
            chatHistoryStore.upsert(
                ChatEntry(
                    id = resolvedChatId,
                    task = task,
                    timestamp = System.currentTimeMillis(),
                    turns = listOf(ChatTurn("user", task)),
                    status = ChatStatus.RUNNING,
                ),
            )
        }

        val service = HandrailAccessibilityService.instance
        if (service == null) {
            val message = str(R.string.assist_a11y_not_enabled)
            showIdleMessage(message)
            appendChatTurn(resolvedChatId, message, ChatStatus.ERROR)
            speakErrorAsync()
            return
        }

        var maxStepSeen = 0
        // Golden border on the accessibility service's own overlay window,
        // not this Activity's — this Activity backgrounds on every step that
        // navigates into another app, so a glow tied to its window would
        // vanish the moment there's anything else to see it react to.
        service.showAgentGlow()
        // Runs in the service's scope, not lifecycleScope: this Activity gets
        // backgrounded every time a step navigates into another app, which
        // would otherwise cancel the loop mid-task. That scope dispatches on
        // Dispatchers.Main, so touching `window` directly from this callback
        // (see the Blocked branch) is safe.
        agentJob = service.backgroundScope.launch {
            val executor = ActionExecutor(service)
            val loop = AgentLoop(service, executor, llmClient, bulbulClient, player, service.backgroundScope, voice)
            loop.run(task, initialHistory) { event ->
                when (event) {
                    is AgentEvent.Step -> {
                        maxStepSeen = maxOf(maxStepSeen, event.index)
                        voiceState = VoiceState.Acting
                        if (event.description.isNotBlank()) narrationLine = event.description
                        stepLabel = str(R.string.step_label_format, event.index)
                        appendChatTurn(resolvedChatId, event.description, ChatStatus.RUNNING, stepCount = maxStepSeen)
                    }
                    is AgentEvent.Done -> {
                        service.hideAgentGlow()
                        pendingAgentContext = null
                        voiceState = VoiceState.Speaking
                        narrationLine = event.summary
                        stepLabel = str(R.string.task_complete)
                        appendChatTurn(resolvedChatId, event.summary, ChatStatus.DONE, stepCount = maxStepSeen)
                    }
                    is AgentEvent.AskUser -> {
                        // Waiting on the user, not actively acting — glow
                        // pauses same as a real block, resumes via a fresh
                        // showAgentGlow() when the answer restarts the loop.
                        service.hideAgentGlow()
                        // Resumable: tapping the caption (see onTapToAnswer)
                        // or typing on the next Home visit answers this and
                        // continues the SAME task, instead of starting a
                        // fresh one with no memory of what was asked.
                        pendingAgentContext = PendingAgentContext(task, event.history, resolvedChatId)
                        voiceState = VoiceState.Idle
                        narrationLine = event.question
                        stepLabel = str(R.string.tap_to_answer)
                        appendChatTurn(resolvedChatId, event.question, ChatStatus.ASK_USER, event.history, stepCount = maxStepSeen)
                    }
                    is AgentEvent.Blocked -> {
                        service.hideAgentGlow()
                        // Genuine hard stop — never auto-resumed. Only the
                        // guard's own trip gets the gold hand-back card; the
                        // other three causes are failures, not a safety beat.
                        pendingAgentContext = null
                        appendChatTurn(
                            resolvedChatId,
                            event.reason,
                            ChatStatus.BLOCKED,
                            stepCount = maxStepSeen,
                            blockCause = event.cause,
                            handbackLabel = event.actionLabel,
                        )
                        if (event.cause == BlockCause.IRREVERSIBLE_GUARD) {
                            overlayScreen = OverlayScreen.Handback(
                                actionLabel = event.actionLabel ?: str(R.string.handback_default_action_label),
                                spokenReason = event.reason,
                            )
                            applyWindowMode(modal = true)
                        } else {
                            showIdleMessage(event.reason)
                        }
                    }
                    is AgentEvent.Error -> {
                        service.hideAgentGlow()
                        pendingAgentContext = null
                        showIdleMessage(event.message)
                        appendChatTurn(resolvedChatId, event.message, ChatStatus.ERROR, stepCount = maxStepSeen)
                    }
                }
            }
        }
    }

    private fun appendChatTurn(
        chatId: String,
        text: String,
        status: ChatStatus,
        agentHistory: List<String>? = null,
        stepCount: Int? = null,
        blockCause: BlockCause? = null,
        handbackLabel: String? = null,
    ) {
        val entry = chatHistoryStore.find(chatId) ?: return
        chatHistoryStore.upsert(
            entry.copy(
                turns = entry.turns + ChatTurn("assistant", text, isTaskStep = true),
                status = status,
                agentHistory = agentHistory ?: entry.agentHistory,
                stepCount = stepCount ?: entry.stepCount,
                blockCause = blockCause ?: entry.blockCause,
                handbackLabel = handbackLabel ?: entry.handbackLabel,
            ),
        )
    }

    // --- Hand-back and stop/cancel ---

    /**
     * Handrail never performs the guarded action itself — this only clears
     * the modal so the REAL button underneath is tappable again, then gets
     * out of the way. See HandbackScreen's doc.
     */
    private fun onHandbackConfirm() {
        applyWindowMode(modal = false)
        finish()
    }

    /** Stop / × / "No, stop" / system back — all cancel whatever's running and get out of the way. */
    private fun onStopRequested() {
        agentJob?.cancel()
        // Cancellation skips straight past the loop's own onEvent branches
        // (that's what cancel() means), so the glow needs its own hide here.
        HandrailAccessibilityService.instance?.hideAgentGlow()
        player.stop()
        finish()
    }

    // --- Shared error narration ---

    /** Best-effort spoken fallback so a failure is never silent. Never itself throws. */
    private fun speakErrorAsync() {
        lifecycleScope.launch { speakError() }
    }

    private suspend fun speakError() {
        val v = voice
        player.playStream(bulbulClient.httpClient, bulbulClient.streamRequest(ErrorPhrases.couldNotDoThat(v.language.code), v.language.code, v.speaker.id, v.pace))
            .onFailure { Log.e(TAG, "Couldn't even speak the error narration", it) }
    }

    companion object {
        const val EXTRA_AGENT_TASK = "agent_task"
        const val EXTRA_CHAT_ID = "chat_id"
        const val EXTRA_AGENT_HISTORY = "agent_history"

        val NARRATION_SYSTEM_PROMPT = """
            You are Handrail, a voice assistant that describes Android phone screens to a user who cannot easily see or read the screen themselves. You will receive a compact list of UI elements from the current screen. Each line is one element in the format: ref className "label" flags bounds=[x,y]. The ref codes and bounds are for internal use only — never speak them.

            Respond with a short spoken narration in %LANGUAGE% describing what is on screen and what the user can do next. One to three short sentences. No markdown, no lists, no element refs, no coordinates. Plain spoken language only.
        """.trimIndent()
    }
}
