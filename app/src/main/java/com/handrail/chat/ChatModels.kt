package com.handrail.chat

import com.handrail.agent.BlockCause
import kotlinx.serialization.Serializable

@Serializable
data class ChatTurn(
    val role: String,
    val text: String,
    /** True for a turn the agent loop produced while running a task (as opposed to a plain chat reply) — lets a thread that starts as chat and later runs a task mark where the task began. */
    val isTaskStep: Boolean = false,
)

enum class ChatStatus { RUNNING, DONE, ASK_USER, BLOCKED, ERROR }

/** Distinguishes a completed takeover task from a narration-mode read — History's two note styles differ, and only TASK entries have step counts. */
enum class EntryKind { TASK, NARRATION }

@Serializable
data class ChatEntry(
    val id: String,
    val task: String,
    val timestamp: Long,
    val turns: List<ChatTurn>,
    /** AgentLoop's own history format, kept separately from [turns] so a resumed run can feed it straight back in. */
    val agentHistory: List<String> = emptyList(),
    val status: ChatStatus = ChatStatus.RUNNING,
    /** Highest [com.handrail.agent.AgentEvent.Step.index] seen this run — already emitted by AgentLoop, just retained here for History's "N steps" note. */
    val stepCount: Int = 0,
    /** Set only when [status] is BLOCKED — which of the four stop reasons, so History can tell a real hand-back from a failure. */
    val blockCause: BlockCause? = null,
    /** The blocked button's own visible text (e.g. "Pay ₹450") — set only for [BlockCause.IRREVERSIBLE_GUARD]. */
    val handbackLabel: String? = null,
    val kind: EntryKind = EntryKind.TASK,
)
