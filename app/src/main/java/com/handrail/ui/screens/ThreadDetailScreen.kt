package com.handrail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handrail.R
import com.handrail.chat.ChatEntry
import com.handrail.chat.ChatStatus
import com.handrail.chat.ChatTurn
import com.handrail.ui.components.Hairline
import com.handrail.ui.components.HandrailTextField
import com.handrail.ui.components.Kicker
import com.handrail.ui.components.Pill
import com.handrail.ui.components.SendButton
import com.handrail.ui.icons.HandrailIcons
import com.handrail.ui.theme.HandrailColors
import com.handrail.ui.theme.HandrailType

/**
 * The screen the design doesn't have: a Conversations row's destination.
 * Turns are drawn as bubbles — right/gold-stroked for the user, left/paper-
 * filled for Handrail — built from the same tokens as the rest of the
 * design (Cormorant kicker labels, Lora body copy, hairline-weight strokes,
 * gold used only as a stroke, never a solid fill) so the bubble treatment
 * reads as this design system's own rather than a generic messaging look.
 * Carries its own message row so a thread can keep going here — chat for a
 * while, then ask for a task, all in the same conversation — instead of
 * being read-only. A thread that starts as chat and then runs a task gets
 * a "Task" tag right where the task turns begin, instead of that fact
 * being invisible once it's folded into one merged History row.
 */
@Composable
fun ThreadDetailScreen(
    entry: ChatEntry?,
    onBack: () -> Unit,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
) {
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
            Kicker(text = stringResource(R.string.conversation_title), fontSize = 15.sp, letterSpacing = 0.2.em)
            Spacer(Modifier.width(32.dp))
        }
        Hairline()

        if (entry == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.conversation_gone), style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 15.sp), color = HandrailColors.Neutral600)
            }
            return
        }

        Text(
            text = entry.task,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 16.dp),
            style = TextStyle(fontFamily = HandrailType.Cormorant, fontSize = 23.sp, lineHeight = 27.sp),
            color = HandrailColors.Text,
        )

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 22.dp)) {
            itemsIndexed(entry.turns) { index, turn ->
                val taskStartsHere = turn.isTaskStep && (index == 0 || !entry.turns[index - 1].isTaskStep)
                if (taskStartsHere) TaskStartTag()
                TurnRow(turn)
            }
            if (entry.status == ChatStatus.RUNNING) {
                item { ThinkingRow() }
            }
        }

        Hairline()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 12.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val busy = entry.status == ChatStatus.RUNNING
            HandrailTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = "Reply or ask for something else",
                enabled = !busy,
                onSend = onSend,
            )
            SendButton(onClick = onSend)
        }
    }
}

/** Bubble corner radius — larger than [com.handrail.ui.theme.HandrailDimens.RadiusLg] on purpose, the one place in this design that reads as "chat." */
private val BubbleRadius = 16.dp
private val BubbleTailRadius = 4.dp

private fun bubbleShape(isUser: Boolean) = RoundedCornerShape(
    topStart = BubbleRadius,
    topEnd = BubbleRadius,
    bottomStart = if (isUser) BubbleRadius else BubbleTailRadius,
    bottomEnd = if (isUser) BubbleTailRadius else BubbleRadius,
)

@Composable
private fun Bubble(isUser: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val fill = if (isUser) HandrailColors.Neutral100 else HandrailColors.Surface
    val stroke = if (isUser) HandrailColors.Accent else HandrailColors.Divider
    Box(
        modifier = modifier
            .background(fill, bubbleShape(isUser))
            .border(1.dp, stroke, bubbleShape(isUser))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) { content() }
}

@Composable
private fun ThinkingRow() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.Start) {
        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.fillMaxWidth(0.82f)) {
            Kicker(text = stringResource(R.string.app_name), fontSize = 11.sp, letterSpacing = 0.16.em, color = HandrailColors.Accent700)
            Spacer(Modifier.height(4.dp))
            Bubble(isUser = false) {
                Text(
                    text = stringResource(R.string.home_caption_thinking),
                    style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 15.5.sp, lineHeight = 23.sp, fontStyle = FontStyle.Italic),
                    color = HandrailColors.Neutral600,
                )
            }
        }
    }
}

@Composable
private fun TaskStartTag() {
    Box(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Pill(text = stringResource(R.string.history_task_tag), on = true)
    }
}

@Composable
private fun TurnRow(turn: ChatTurn) {
    val isUser = turn.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.82f),
        ) {
            Kicker(
                text = if (isUser) stringResource(R.string.you_label) else stringResource(R.string.app_name),
                fontSize = 11.sp,
                letterSpacing = 0.16.em,
                color = if (isUser) HandrailColors.Neutral600 else HandrailColors.Accent700,
            )
            Spacer(Modifier.height(4.dp))
            Bubble(isUser = isUser) {
                Text(
                    text = turn.text,
                    style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 15.5.sp, lineHeight = 23.sp),
                    color = HandrailColors.Text,
                )
            }
        }
    }
}
