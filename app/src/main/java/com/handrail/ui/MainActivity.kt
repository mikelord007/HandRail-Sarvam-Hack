package com.handrail.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.handrail.chat.ChatEntry
import com.handrail.chat.ChatHistoryStore
import com.handrail.chat.ChatStatus
import com.handrail.chat.ChatTurn
import com.handrail.sarvam.ChatMessage
import com.handrail.sarvam.LlmClient
import com.handrail.speech.AudioRecorder
import com.handrail.speech.BulbulClient
import com.handrail.speech.ErrorPhrases
import com.handrail.speech.Language
import com.handrail.speech.NarrationPlayer
import com.handrail.speech.SaarasClient
import com.handrail.speech.Speaker
import com.handrail.speech.Speakers
import com.handrail.speech.SupportedLanguages
import com.handrail.speech.VoicePreferences
import com.handrail.speech.VoiceSettings
import com.handrail.ui.screens.HistoryScreen
import com.handrail.ui.screens.HomeScreen
import com.handrail.ui.screens.SettingsScreen
import com.handrail.ui.screens.ThreadDetailScreen
import com.handrail.ui.screens.onboarding.AccessibilityScreen
import com.handrail.ui.screens.onboarding.AssistantScreen
import com.handrail.ui.screens.onboarding.LanguageScreen
import com.handrail.ui.screens.onboarding.MicScreen
import com.handrail.ui.screens.onboarding.PromiseScreen
import com.handrail.ui.screens.onboarding.VoiceScreen
import com.handrail.ui.screens.onboarding.WelcomeScreen
import com.handrail.ui.theme.HandrailTheme
import java.util.UUID
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

/** For a CHAT-classified turn: no tool calls, no screen actions, just a spoken reply. */
private val CHAT_SYSTEM_PROMPT = """
    You are Handrail, a friendly voice assistant. Reply conversationally in %LANGUAGE%, in one to three short spoken sentences. No markdown, no lists. You are not performing any action on the user's phone right now — you're just talking with them.
""".trimIndent()

/**
 * Routes every Home/thread message before anything else runs, so a wrong
 * call here either skips a real task (stays CHAT) or wrongly opens the
 * takeover overlay for small talk (calls TASK). Strict + few-shot per
 * CLAUDE.md's "assume the weaker model" guidance. Sees the whole thread so
 * far, not just the latest line, so a task request that only makes sense
 * after prior chat turns (mid-conversation switch) still classifies right.
 */
private val INTENT_CLASSIFIER_PROMPT = """
    You classify the LATEST message in a conversation with a phone voice assistant, using the earlier turns only as context for what "it"/"that" might refer to.

    Reply TASK if the latest message asks the assistant to perform an action on the phone: open an app, search for something, book/order/pay for something, navigate somewhere, or otherwise tap/scroll/type on a screen.
    Reply CHAT if it's a question, statement, or small talk that needs no on-screen action.

    Respond with exactly one word: TASK or CHAT. No punctuation, no explanation, nothing else.

    Examples:
    User: hey
    CHAT
    User: what's the capital of France
    CHAT
    User: book a cab home
    TASK
    User: open Chrome and search for weather
    TASK
    User: tell me a joke
    CHAT
    User: pay the electricity bill
    TASK
    User: I need to get to the airport by 6
    CHAT
    User: yeah go ahead and book it
    TASK
""".trimIndent()

/**
 * Hosts every screen except the translucent assist overlay (that's
 * [AssistActivity] — see [Screen]'s class doc for why they're separate).
 * The app's launcher entry point; the only door that leads to Home.
 */
class MainActivity : ComponentActivity() {

    private val chatHistoryStore by lazy { ChatHistoryStore(applicationContext) }
    private val voicePreferences by lazy { VoicePreferences(applicationContext) }
    private val onboardingPreferences by lazy { OnboardingPreferences(applicationContext) }
    private val audioRecorder by lazy { AudioRecorder(applicationContext) }
    private val saarasClient = SaarasClient()
    private val bulbulClient = BulbulClient()
    private val llmClient = LlmClient()
    private val narrationPlayer by lazy { NarrationPlayer(applicationContext) }

    private var entries by mutableStateOf<List<ChatEntry>>(emptyList())
    private var draft by mutableStateOf("")
    /** [ThreadDetailScreen]'s own input — separate from Home's [draft] so opening a thread doesn't leak Home's leftover text into it. */
    private var threadDraft by mutableStateOf("")
    private var isRecording by mutableStateOf(false)
    private var isTranscribing by mutableStateOf(false)
    private var isThinking by mutableStateOf(false)

    private var selectedLanguage by mutableStateOf(SupportedLanguages.DEFAULT)
    private var selectedSpeaker by mutableStateOf(Speakers.DEFAULT)
    private var narrateEveryStep by mutableStateOf(true)
    private var speakSlowly by mutableStateOf(false)
    private var permissions by mutableStateOf(Permissions(microphone = false, accessibility = false, defaultAssistant = false))

    /** A class field, not a `remember` inside setContent, so [submitTask] can navigate to the new thread as soon as it's created. */
    private lateinit var nav: HandrailNav

    /** Home's mic control — starts a takeover/narration task, so a grant here should immediately start recording. */
    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startRecording()
    }

    /** Onboarding's "Allow microphone" — per the design, this advances regardless of the result; the grant itself is all this needs to do. */
    private val requestMicPermissionOnboarding = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedLanguage = voicePreferences.language
        selectedSpeaker = voicePreferences.speaker
        narrateEveryStep = voicePreferences.narrateEveryStep
        speakSlowly = voicePreferences.speakSlowly
        nav = HandrailNav(if (onboardingPreferences.isComplete) Screen.Home else Screen.Welcome)

        setContent {
            HandrailTheme {
                BackHandler(enabled = nav.canGoBack) { nav.back() }

                when (val screen = nav.current) {
                    // --- Onboarding: welcome -> language -> voice -> mic -> a11y -> assistant -> promise -> home ---
                    Screen.Welcome -> WelcomeScreen(onBegin = { nav.go(Screen.Language) })
                    Screen.Language -> LanguageScreen(
                        languages = SupportedLanguages.ALL,
                        selected = selectedLanguage,
                        onSelected = ::onLanguageSelected,
                        onContinue = { nav.go(Screen.Voice) },
                        onBack = nav::back,
                    )
                    Screen.Voice -> VoiceScreen(
                        speakers = Speakers.ALL,
                        selected = selectedSpeaker,
                        onSelected = ::onSpeakerSelected,
                        onContinue = { nav.go(Screen.MicPermission) },
                        onBack = nav::back,
                    )
                    Screen.MicPermission -> MicScreen(
                        onAllow = { requestMicPermissionOnboarding.launch(Manifest.permission.RECORD_AUDIO); nav.go(Screen.AccessibilityPermission) },
                        onNotNow = { nav.go(Screen.AccessibilityPermission) },
                        onBack = nav::back,
                    )
                    Screen.AccessibilityPermission -> AccessibilityScreen(
                        onOpenSettings = { openAccessibilitySettings(); nav.go(Screen.AssistantPermission) },
                        onBack = nav::back,
                    )
                    Screen.AssistantPermission -> AssistantScreen(
                        onSetAsAssistant = { openAssistantSettings(); nav.go(Screen.Promise) },
                        onNotNow = { nav.go(Screen.Promise) },
                        onBack = nav::back,
                    )
                    Screen.Promise -> PromiseScreen(
                        onUnderstood = {
                            onboardingPreferences.isComplete = true
                            nav.reset(Screen.Home)
                        },
                    )

                    // --- Everyday use ---
                    Screen.Home -> HomeScreen(
                        language = selectedLanguage,
                        recentEntries = entries.sortedByDescending { it.timestamp },
                        draft = draft,
                        onDraftChange = { draft = it },
                        isRecording = isRecording,
                        isTranscribing = isTranscribing,
                        isThinking = isThinking,
                        onMicTap = ::onMicToggle,
                        onSuggestionTap = ::submitTask,
                        onSend = ::onSend,
                        onOpenHistory = { nav.go(Screen.History) },
                        onOpenSettings = { nav.go(Screen.Settings) },
                    )
                    Screen.History -> HistoryScreen(
                        entries = entries,
                        onBack = nav::back,
                        onOpenThread = { chatId -> threadDraft = ""; nav.go(Screen.ThreadDetail(chatId)) },
                    )
                    is Screen.ThreadDetail -> ThreadDetailScreen(
                        entry = entries.firstOrNull { it.id == screen.chatId },
                        onBack = nav::back,
                        draft = threadDraft,
                        onDraftChange = { threadDraft = it },
                        onSend = { onThreadSend(screen.chatId) },
                    )
                    Screen.Settings -> SettingsScreen(
                        onBack = nav::back,
                        languages = SupportedLanguages.ALL,
                        selectedLanguage = selectedLanguage,
                        onLanguageSelected = ::onLanguageSelected,
                        permissions = permissions,
                        onOpenAccessibilitySettings = ::openAccessibilitySettings,
                        onOpenMicrophoneSettings = ::openMicrophoneSettings,
                        onOpenAssistantSettings = ::openAssistantSettings,
                        narrateEveryStep = narrateEveryStep,
                        onNarrateToggle = ::onNarrateToggle,
                        speakSlowly = speakSlowly,
                        onSpeakSlowlyToggle = ::onSpeakSlowlyToggle,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        reloadHistory()
        permissions = PermissionStatus.snapshot(this)
    }

    private fun reloadHistory() {
        entries = chatHistoryStore.loadAll()
    }

    // --- Task submission (typed, spoken, or a suggestion tap) ---

    private fun onSend() {
        val text = draft.trim()
        if (text.isEmpty()) return
        draft = ""
        submitTask(text)
    }

    private fun onThreadSend(chatId: String) {
        val text = threadDraft.trim()
        if (text.isEmpty()) return
        threadDraft = ""
        continueThread(chatId, text)
    }

    /**
     * A fresh message from Home — always starts a brand-new thread and
     * navigates straight into it, before any reply arrives, so the exchange
     * plays out live like a normal chat. [continueThread] is the same idea
     * for a message sent from inside an already-open thread.
     */
    private fun submitTask(userText: String) {
        val entry = ChatEntry(
            id = UUID.randomUUID().toString(),
            task = userText,
            timestamp = System.currentTimeMillis(),
            turns = listOf(ChatTurn("user", userText)),
            status = ChatStatus.RUNNING,
        )
        chatHistoryStore.upsert(entry)
        reloadHistory()
        nav.go(Screen.ThreadDetail(entry.id))
        respondTo(entry)
    }

    private fun continueThread(chatId: String, userText: String) {
        val existing = chatHistoryStore.find(chatId) ?: return
        val entry = existing.copy(turns = existing.turns + ChatTurn("user", userText), status = ChatStatus.RUNNING)
        chatHistoryStore.upsert(entry)
        reloadHistory()
        respondTo(entry, resumingAskUser = existing.status == ChatStatus.ASK_USER)
    }

    /**
     * Routes one user turn. An answer to a paused ask_user resumes that SAME
     * takeover run directly — a bare "yes" would otherwise misclassify as
     * CHAT. Otherwise an LLM classifier decides: TASK hands off to
     * [AssistActivity]'s agent loop (the translucent overlay only ever
     * appears once a task is actually confirmed this way, or via the real
     * ASSIST invocation over another app); CHAT stays a plain conversational
     * reply, spoken and shown in place, same as before.
     */
    private fun respondTo(entry: ChatEntry, resumingAskUser: Boolean = false) {
        isThinking = true
        lifecycleScope.launch {
            if (resumingAskUser) {
                isThinking = false
                startTakeover(entry.id, entry.task, entry.agentHistory + "User answered: ${entry.turns.last().text}")
                return@launch
            }

            if (classifyAsTask(entry.turns)) {
                isThinking = false
                val taskText = entry.turns.filter { it.role == "user" }.joinToString(" ") { it.text }
                startTakeover(entry.id, taskText)
                return@launch
            }

            val v = voicePreferences.settings
            val systemPrompt = CHAT_SYSTEM_PROMPT.replace("%LANGUAGE%", v.language.displayName)
            val result = llmClient.chatCompletion(
                messages = listOf(ChatMessage(role = "system", content = systemPrompt)) +
                    entry.turns.map { ChatMessage(role = if (it.role == "user") "user" else "assistant", content = it.text) },
            )
            isThinking = false

            val replyText = result.getOrNull()?.choices?.firstOrNull()?.message?.content?.trim().orEmpty()
            if (result.isFailure || replyText.isEmpty()) {
                Log.e(TAG, "Chat reply failed", result.exceptionOrNull())
                val errorText = ErrorPhrases.couldNotDoThat(v.language.code)
                chatHistoryStore.upsert(entry.copy(turns = entry.turns + ChatTurn("assistant", errorText), status = ChatStatus.ERROR))
                reloadHistory()
                val audio = bulbulClient.synthesize(errorText, v.language.code, v.speaker.id, v.pace)
                audio.onSuccess { narrationPlayer.play(it) }
                return@launch
            }

            chatHistoryStore.upsert(entry.copy(turns = entry.turns + ChatTurn("assistant", replyText), status = ChatStatus.DONE))
            reloadHistory()

            val audio = bulbulClient.synthesize(replyText, v.language.code, v.speaker.id, v.pace)
            audio.onSuccess { narrationPlayer.play(it) }
        }
    }

    /** Defaults to CHAT (false) on any classifier failure — safer to stay conversational than to wrongly launch the agent loop against whatever's on screen. */
    private suspend fun classifyAsTask(turns: List<ChatTurn>): Boolean {
        val messages = listOf(ChatMessage(role = "system", content = INTENT_CLASSIFIER_PROMPT)) +
            turns.map { ChatMessage(role = if (it.role == "user") "user" else "assistant", content = it.text) }
        val result = llmClient.chatCompletion(messages = messages)
        val label = result.getOrNull()?.choices?.firstOrNull()?.message?.content?.trim()?.uppercase().orEmpty()
        return label.startsWith("TASK")
    }

    /** The only place [AssistActivity] is started from chat — same chat id, so the agent's own steps append into this same visible thread. */
    private fun startTakeover(chatId: String, task: String, initialHistory: List<String> = emptyList()) {
        val intent = Intent(this, AssistActivity::class.java).apply {
            putExtra(AssistActivity.EXTRA_AGENT_TASK, task)
            putExtra(AssistActivity.EXTRA_CHAT_ID, chatId)
            putStringArrayListExtra(AssistActivity.EXTRA_AGENT_HISTORY, ArrayList(initialHistory))
        }
        startActivity(intent)
    }

    // --- Voice input alternative ---

    private fun onMicToggle() {
        if (isRecording) {
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
            isRecording = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording", e)
        }
    }

    private fun stopRecordingAndTranscribe() {
        isRecording = false
        val file = audioRecorder.stop() ?: return
        isTranscribing = true
        lifecycleScope.launch {
            val result = saarasClient.transcribe(file)
            file.delete()
            isTranscribing = false
            result.onSuccess { transcript ->
                if (transcript.isNotBlank()) submitTask(transcript)
            }.onFailure { error ->
                Log.e(TAG, "Saaras STT failed", error)
            }
        }
    }

    // --- Onboarding + Settings: language, speaker, permissions, behaviour toggles ---

    private fun onLanguageSelected(language: Language) {
        voicePreferences.language = language
        selectedLanguage = language
    }

    /** Voice onboarding plays the sample line back immediately, per the design: "in the real app this is spoken aloud on selection." */
    private fun onSpeakerSelected(speaker: Speaker) {
        voicePreferences.speaker = speaker
        selectedSpeaker = speaker
        lifecycleScope.launch {
            val audio = bulbulClient.synthesize(
                "I found Ola. Opening it now.",
                selectedLanguage.code,
                speaker.id,
                VoiceSettings.PACE_NORMAL,
            )
            audio.onSuccess { narrationPlayer.play(it) }
        }
    }

    private fun onNarrateToggle(value: Boolean) {
        voicePreferences.narrateEveryStep = value
        narrateEveryStep = value
    }

    private fun onSpeakSlowlyToggle(value: Boolean) {
        voicePreferences.speakSlowly = value
        speakSlowly = value
    }

    private fun openAccessibilitySettings() {
        startActivity(PermissionStatus.accessibilitySettingsIntent())
    }

    private fun openMicrophoneSettings() {
        startActivity(PermissionStatus.appDetailsSettingsIntent(this))
    }

    private fun openAssistantSettings() {
        try {
            startActivity(PermissionStatus.assistantSettingsIntent())
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "ACTION_VOICE_INPUT_SETTINGS not available on this ROM, falling back", e)
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }
}
