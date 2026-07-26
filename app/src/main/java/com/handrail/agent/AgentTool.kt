package com.handrail.agent

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

sealed interface AgentTool {
    data class Tap(val ref: String, val say: String) : AgentTool
    data class SetText(val ref: String, val text: String, val say: String) : AgentTool
    data class Scroll(val direction: String, val say: String) : AgentTool
    data class Back(val say: String) : AgentTool
    data class Home(val say: String) : AgentTool
    data class Done(val summary: String) : AgentTool
    data class AskUser(val question: String) : AgentTool
    data class Blocked(val reason: String) : AgentTool
}

/**
 * Parses the model's single-line JSON tool call. Tolerant of stray prose or
 * markdown fences around the JSON (weaker models don't always follow
 * instructions exactly) — returns null on anything it can't make sense of,
 * so the caller can retry or bail per the malformed-twice-in-a-row rule.
 */
object AgentToolParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(raw: String): AgentTool? {
        val jsonText = extractJsonObject(raw) ?: return null
        return try {
            val obj = json.parseToJsonElement(jsonText).jsonObject
            when (obj.stringOrNull("tool")) {
                "tap" -> AgentTool.Tap(
                    ref = obj.stringOrNull("ref") ?: return null,
                    say = obj.stringOrNull("say").orEmpty(),
                )
                "set_text" -> AgentTool.SetText(
                    ref = obj.stringOrNull("ref") ?: return null,
                    text = obj.stringOrNull("text") ?: return null,
                    say = obj.stringOrNull("say").orEmpty(),
                )
                "scroll" -> AgentTool.Scroll(
                    direction = obj.stringOrNull("direction") ?: return null,
                    say = obj.stringOrNull("say").orEmpty(),
                )
                "back" -> AgentTool.Back(say = obj.stringOrNull("say").orEmpty())
                "home" -> AgentTool.Home(say = obj.stringOrNull("say").orEmpty())
                "done" -> AgentTool.Done(summary = obj.stringOrNull("summary") ?: return null)
                "ask_user" -> AgentTool.AskUser(question = obj.stringOrNull("question") ?: return null)
                "blocked" -> AgentTool.Blocked(reason = obj.stringOrNull("reason") ?: return null)
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun JsonObject.stringOrNull(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private fun extractJsonObject(raw: String): String? {
        val start = raw.indexOf('{')
        val end = raw.lastIndexOf('}')
        if (start == -1 || end == -1 || end < start) return null
        return raw.substring(start, end + 1)
    }
}
