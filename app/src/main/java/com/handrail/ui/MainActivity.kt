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

/** Plain conversation only — no tool calls, no screen actions. Takeover mode lives in AssistActivity, reached only via the real ASSIST invocation. */
private val CHAT_SYSTEM_PROMPT = """
    You are Handrail, a friendly voice assistant. Reply conversationally in %LANGUAGE%, in one to three short spoken sentences. No markdown, no lists. You cannot see the user's screen or perform actions on their phone from this conversation — if asked to do something on-screen, tell them to long-press their home button to invoke you as their assistant on that screen.
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
                        onOpenThread = { chatId -> nav.go(Screen.ThreadDetail(chatId)) },
                    )
                    is Screen.ThreadDetail -> ThreadDetailScreen(
                        entry = entries.firstOrNull { it.id == screen.chatId },
                        onBack = nav::back,
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

    /**
     * A plain, spoken conversation turn — no overlay, no agent tool-calling.
     * The translucent takeover/narration overlay ([AssistActivity]) is
     * reserved for the real ASSIST invocation (long-press home) over
     * whatever app/screen is in the foreground at the time; chatting on
     * Handrail's own Home screen never leaves this Activity. Navigates to
     * the new entry's [ThreadDetailScreen] immediately, before the reply
     * arrives, so the exchange plays out live like a normal chat.
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

        isThinking = true
        lifecycleScope.launch {
            val v = voicePreferences.settings
            val systemPrompt = CHAT_SYSTEM_PROMPT.replace("%LANGUAGE%", v.language.displayName)
            val result = llmClient.chatCompletion(
                messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
                    ChatMessage(role = "user", content = userText),
                ),
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
