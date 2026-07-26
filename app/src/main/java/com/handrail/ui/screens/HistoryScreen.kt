package com.handrail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handrail.R
import com.handrail.agent.BlockCause
import com.handrail.chat.ChatEntry
import com.handrail.chat.ChatStatus
import com.handrail.chat.EntryKind
import com.handrail.ui.components.Hairline
import com.handrail.ui.components.Kicker
import com.handrail.ui.formatRelativeTime
import com.handrail.ui.icons.HandrailIcons
import com.handrail.ui.theme.HandrailColors
import com.handrail.ui.theme.HandrailType

private enum class HistoryTab { Tasks, Conversations }

/**
 * The "quiet half" of the app — a flat receipt list of what Handrail did,
 * what it cost (in steps), and what it refused. Tapping any row opens
 * [ThreadDetailScreen] — the design's own Conversations rows go nowhere
 * (they just return to home), which loses the ability to read what the
 * agent actually said; see the plan's deviations table.
 */
@Composable
fun HistoryScreen(entries: List<ChatEntry>, onBack: () -> Unit, onOpenThread: (String) -> Unit) {
    var tab by remember { mutableStateOf(HistoryTab.Tasks) }
    val sorted = entries.sortedByDescending { it.timestamp }
    val conversations = sorted.filter { it.turns.count { turn -> turn.role == "user" } > 1 }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(32.dp).clickable(onClick = onBack),
                contentAlignment = Alignment.CenterStart,
            ) {
                Icon(imageVector = HandrailIcons.ArrowLeft, contentDescription = stringResource(R.string.back), tint = HandrailColors.Neutral700, modifier = Modifier.size(20.dp))
            }
            Kicker(text = stringResource(R.string.history_title), fontSize = 15.sp, letterSpacing = 0.2.em)
            Spacer(Modifier.width(32.dp))
        }
        Hairline()

        Row(modifier = Modifier.fillMaxWidth()) {
            HistoryTabButton(text = stringResource(R.string.tab_tasks), active = tab == HistoryTab.Tasks, modifier = Modifier.weight(1f)) { tab = HistoryTab.Tasks }
            HistoryTabButton(text = stringResource(R.string.tab_conversations), active = tab == HistoryTab.Conversations, modifier = Modifier.weight(1f)) { tab = HistoryTab.Conversations }
        }

        val rows = if (tab == HistoryTab.Tasks) sorted else conversations
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp)) {
            items(rows, key = { it.id }) { entry ->
                HistoryRow(
                    title = entry.task,
                    time = formatRelativeTime(entry.timestamp),
                    note = if (tab == HistoryTab.Tasks) taskNote(entry) else conversationNote(entry),
                    onClick = { onOpenThread(entry.id) },
                )
            }
        }
    }
}

@Composable
private fun HistoryTabButton(text: String, active: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val lineColor = if (active) HandrailColors.Accent else androidx.compose.ui.graphics.Color.Transparent
    val ink = if (active) HandrailColors.Text else HandrailColors.Neutral600
    Box(
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick)
            .background(androidx.compose.ui.graphics.Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Kicker(text = text, fontSize = 15.sp, letterSpacing = 0.14.em, color = ink)
        }
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(lineColor).align(Alignment.BottomCenter))
    }
}

@Composable
private fun HistoryRow(title: String, time: String, note: String, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp)) {
        Row(horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 16.5.sp),
                color = HandrailColors.Text,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = time,
                style = TextStyle(fontFamily = HandrailType.Cormorant, fontSize = 13.sp).merge(HandrailType.TabularFigures),
                color = HandrailColors.Neutral600,
            )
        }
        Text(
            text = note,
            modifier = Modifier.padding(top = 5.dp),
            style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 14.sp, fontStyle = FontStyle.Italic),
            color = HandrailColors.Neutral600,
        )
        Spacer(Modifier.height(16.dp))
        Hairline()
    }
}

@Composable
private fun taskNote(entry: ChatEntry): String {
    if (entry.kind == EntryKind.NARRATION) return stringResource(R.string.note_narrated)
    return when (entry.status) {
        ChatStatus.RUNNING -> stringResource(R.string.note_in_progress)
        ChatStatus.ASK_USER -> stringResource(R.string.note_waiting_answer)
        ChatStatus.DONE -> if (entry.stepCount > 0) pluralStringResource(R.plurals.steps_count, entry.stepCount, entry.stepCount) else stringResource(R.string.note_done)
        ChatStatus.BLOCKED -> when (entry.blockCause) {
            BlockCause.IRREVERSIBLE_GUARD -> {
                val stepsPart = if (entry.stepCount > 0) pluralStringResource(R.plurals.steps_count, entry.stepCount, entry.stepCount) + " · " else ""
                stepsPart + stringResource(R.string.note_handback_at, entry.handbackLabel ?: "")
            }
            else -> stringResource(R.string.note_stopped, lastAssistantText(entry))
        }
        ChatStatus.ERROR -> stringResource(R.string.note_couldnt_finish, lastAssistantText(entry))
    }
}

@Composable
private fun conversationNote(entry: ChatEntry): String {
    val replies = entry.turns.count { it.role == "assistant" }
    return stringResource(R.string.conversation_note, entry.task, pluralStringResource(R.plurals.replies_count, replies, replies))
}

@Composable
private fun lastAssistantText(entry: ChatEntry): String =
    entry.turns.lastOrNull { it.role == "assistant" }?.text?.take(60) ?: stringResource(R.string.unknown_reason)
