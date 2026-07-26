package com.handrail.agent

import com.handrail.actions.ActionExecutor
import com.handrail.actions.ActionResult
import com.handrail.actions.ScrollDirection
import com.handrail.perception.HandrailAccessibilityService
import com.handrail.sarvam.ChatMessage
import com.handrail.sarvam.LlmClient
import com.handrail.speech.BulbulClient
import com.handrail.speech.ErrorPhrases
import com.handrail.speech.HandbackPhrases
import com.handrail.speech.NarrationPlayer
import com.handrail.speech.VoiceSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Why the loop stopped with [AgentEvent.Blocked] — the hand-back UI only shows the gold card for [IRREVERSIBLE_GUARD]; the other three are failures. */
enum class BlockCause { IRREVERSIBLE_GUARD, MODEL_DECLARED, STEP_BUDGET, MALFORMED_OUTPUT }

sealed interface AgentEvent {
    data class Step(val index: Int, val description: String) : AgentEvent
    data class Done(val summary: String) : AgentEvent

    /**
     * Not a hard stop — [history] is the conversation-so-far snapshot so a
     * caller can resume the SAME task once the user answers, instead of
     * starting a brand-new task with no memory of the question.
     */
    data class AskUser(val question: String, val history: List<String>) : AgentEvent

    /**
     * [actionLabel] is only ever set for [BlockCause.IRREVERSIBLE_GUARD] —
     * the blocked node's own visible text (e.g. "Pay ₹450"), for the
     * hand-back card to show the real action instead of generic copy.
     */
    data class Blocked(val reason: String, val cause: BlockCause, val actionLabel: String? = null) : AgentEvent
    data class Error(val message: String) : AgentEvent
}

/**
 * Takeover-mode loop: perceive -> model picks one tool call -> narrate ->
 * execute -> re-perceive. Every stop condition (done/ask_user/blocked/step
 * budget/malformed output) narrates before ending, per CLAUDE.md.
 *
 * ask_user is the one stop condition callers are expected to resume (see
 * [AgentEvent.AskUser]) — everything else, especially "blocked" from the
 * irreversible-action guard, is a genuine hard stop. The guard's whole point
 * is that the user does the final tap themselves; auto-resuming past it on
 * the next utterance would quietly defeat that.
 */
class AgentLoop(
    private val service: HandrailAccessibilityService,
    private val executor: ActionExecutor,
    private val llmClient: LlmClient,
    private val bulbulClient: BulbulClient,
    private val player: NarrationPlayer,
    private val scope: CoroutineScope,
    private val voice: VoiceSettings,
) {

    private val language get() = voice.language

    suspend fun run(task: String, initialHistory: List<String> = emptyList(), onEvent: (AgentEvent) -> Unit) {
        val history = initialHistory.toMutableList()

        for (step in 1..MAX_STEPS) {
            var perception = service.captureScreen(goHomeIfEmpty = step == 1)
            // A window with zero actionable elements after step 1 is usually
            // an app mid-launch (e.g. a splash screen) rather than a genuine
            // dead end — give it a moment to finish rendering before bailing.
            var settleAttempts = 0
            while (step > 1 && (perception?.refMap?.isEmpty() != false) && settleAttempts < MAX_EMPTY_SCREEN_RETRIES) {
                settleAttempts++
                service.awaitContentChanged(800L)
                perception = service.captureScreen()
            }
            if (perception == null || perception.refMap.isEmpty()) {
                val message = ErrorPhrases.couldNotDoThat(language.code)
                speak(message)
                onEvent(AgentEvent.Error(message))
                return
            }

            if (LoginWallDetector.isLoginWall(perception)) {
                val message = ErrorPhrases.loginOrOtpDetected(language.code)
                speak(message)
                history += "Asked user: $message"
                onEvent(AgentEvent.AskUser(message, history.toList()))
                return
            }

            var tool: AgentTool? = null
            var attempts = 0
            while (tool == null && attempts < MAX_MALFORMED_RETRIES) {
                attempts++
                val raw = requestNextAction(task, history, perception.serialized, retryHint = attempts > 1)
                tool = raw.getOrNull()?.let(AgentToolParser::parse)
            }

            val resolvedTool = tool ?: run {
                val message = ErrorPhrases.stoppedForYou(language.code)
                speak(message)
                onEvent(AgentEvent.Blocked(message, BlockCause.MALFORMED_OUTPUT))
                return
            }

            when (val t = resolvedTool) {
                is AgentTool.Done -> {
                    speak(t.summary)
                    onEvent(AgentEvent.Done(t.summary))
                    return
                }
                is AgentTool.AskUser -> {
                    speak(t.question)
                    history += "Asked user: ${t.question}"
                    onEvent(AgentEvent.AskUser(t.question, history.toList()))
                    return
                }
                is AgentTool.Blocked -> {
                    speak(t.reason)
                    onEvent(AgentEvent.Blocked(t.reason, BlockCause.MODEL_DECLARED))
                    return
                }
                is AgentTool.Tap -> {
                    narrateStep(t.say)
                    val result = executor.tap(t.ref, perception.refMap)
                    if (result is ActionResult.Blocked) {
                        val spoken = HandbackPhrases.aboutToDo(language.code, result.label)
                        speak(spoken)
                        onEvent(AgentEvent.Blocked(spoken, BlockCause.IRREVERSIBLE_GUARD, result.label))
                        return
                    }
                    history += "Step $step: tap(${t.ref}) -> $result"
                    onEvent(AgentEvent.Step(step, t.say))
                }
                is AgentTool.SetText -> {
                    narrateStep(t.say)
                    val result = executor.setText(t.ref, t.text, perception.refMap)
                    history += "Step $step: set_text(${t.ref}, \"${t.text}\") -> $result"
                    onEvent(AgentEvent.Step(step, t.say))
                }
                is AgentTool.Scroll -> {
                    narrateStep(t.say)
                    val direction = if (t.direction.equals("up", ignoreCase = true)) {
                        ScrollDirection.UP
                    } else {
                        ScrollDirection.DOWN
                    }
                    val result = executor.scroll(direction, perception.refMap)
                    history += "Step $step: scroll(${t.direction}) -> $result"
                    onEvent(AgentEvent.Step(step, t.say))
                }
                is AgentTool.Back -> {
                    narrateStep(t.say)
                    val result = executor.back()
                    history += "Step $step: back() -> $result"
                    onEvent(AgentEvent.Step(step, t.say))
                }
                is AgentTool.Home -> {
                    narrateStep(t.say)
                    val result = executor.home()
                    history += "Step $step: home() -> $result"
                    onEvent(AgentEvent.Step(step, t.say))
                }
            }
        }

        val message = ErrorPhrases.stoppedForYou(language.code)
        speak(message)
        onEvent(AgentEvent.Blocked(message, BlockCause.STEP_BUDGET))
    }

    private suspend fun requestNextAction(
        task: String,
        history: List<String>,
        screen: String,
        retryHint: Boolean,
    ): Result<String> {
        val systemPrompt = AGENT_SYSTEM_PROMPT.replace("%LANGUAGE%", language.displayName)
        val userContent = buildString {
            append("TASK: ").append(task).append('\n')
            if (history.isNotEmpty()) {
                append("HISTORY:\n")
                history.forEach { append(it).append('\n') }
            }
            append("SCREEN:\n").append(screen)
            if (retryHint) {
                append(
                    "\n\nYour previous response was not valid JSON matching one of the tool " +
                        "shapes. Respond with exactly one JSON object, nothing else.",
                )
            }
        }
        val result = llmClient.chatCompletion(
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userContent),
            ),
        )
        return result.map { it.choices.firstOrNull()?.message?.content?.trim().orEmpty() }
    }

    /** Per-step narration, gated by the "Narrate every step" preference — terminal outcomes (done/blocked/ask_user) always speak via [speak] directly. */
    private fun narrateStep(text: String) {
        if (!voice.narrateEveryStep) return
        narrateAsync(text)
    }

    private fun narrateAsync(text: String) {
        if (text.isBlank()) return
        scope.launch { speak(text) }
    }

    private suspend fun speak(text: String) {
        if (text.isBlank()) return
        player.playStream(bulbulClient.httpClient, bulbulClient.streamRequest(text, language.code, voice.speaker.id, voice.pace))
    }

    private companion object {
        const val MAX_STEPS = 12
        const val MAX_MALFORMED_RETRIES = 2
        const val MAX_EMPTY_SCREEN_RETRIES = 3

        val AGENT_SYSTEM_PROMPT = """
            You are Handrail, an agent that completes a task on an Android phone on behalf of a user, by looking at the current screen and choosing ONE next action at a time.

            You will be given:
            - TASK: what the user asked for.
            - HISTORY: actions already taken this run and their results, oldest first. This may include a question you already asked the user and their answer — if so, treat the answer as authorization to proceed, don't ask the same question again.
            - SCREEN: the current screen's elements, one per line, format: ref className "label" flags bounds=[x,y]. Refs (e1, e2, ...) are how you refer to elements. Only actionable, currently-visible elements have bounds — an element listed without bounds exists but is off-screen; scroll before trying to target it.

            Respond with EXACTLY one line of JSON. Nothing else — no markdown, no code fences, no explanation before or after.

            Available actions:
            {"tool":"tap","ref":"<ref>","say":"<short spoken narration in %LANGUAGE%, describing what you're about to tap>"}
            {"tool":"set_text","ref":"<ref>","text":"<text to type>","say":"<short spoken narration in %LANGUAGE%>"}
            {"tool":"scroll","direction":"up"|"down","say":"<short spoken narration in %LANGUAGE%>"}
            {"tool":"back","say":"<short spoken narration in %LANGUAGE%>"}
            {"tool":"home","say":"<short spoken narration in %LANGUAGE%>"}
            {"tool":"done","summary":"<short spoken summary in %LANGUAGE% of what was accomplished>"}
            {"tool":"ask_user","question":"<short spoken question in %LANGUAGE% for the user>"}
            {"tool":"blocked","reason":"<short spoken explanation in %LANGUAGE% of why you stopped>"}

            Rules:
            - Use "blocked" instead of "tap" whenever the target button would pay, send, submit, order, transfer, confirm, or buy something — stop and let the user do that tap themselves.
            - Use "ask_user" if the screen shows a password field, OTP entry, or a "verify" prompt — never read or enter passwords or OTPs.
            - Use "done" as soon as the current SCREEN already shows what the task asked for — e.g. if asked to open a settings page and that page is now on screen, call "done" immediately rather than tapping anything else on it.
            - Do not use "ask_user" just to confirm a plan before starting — only ask when you're genuinely unsure which ref to use, or the task itself is ambiguous. If the task is clear (e.g. "open YouTube and play a specific video"), just start doing it; narrate each step via "say" so the user can stop you if they don't like where it's going.
            - If you already asked the user something (see HISTORY) and they replied, do not ask again — act on their answer.

            Example:
            SCREEN:
            e1 EditText "Search settings" clickable editable bounds=[603,147]
            e2 LinearLayout clickable bounds=[540,340]
            e3 TextView "Wi-Fi"
            Response:
            {"tool":"tap","ref":"e2","say":"Wi-Fi खोल रहा हूँ"}
        """.trimIndent()
    }
}
