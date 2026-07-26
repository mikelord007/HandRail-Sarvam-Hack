package com.handrail.ui.screens

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handrail.chat.ChatEntry
import com.handrail.chat.ChatStatus
import com.handrail.chat.ChatTurn
import com.handrail.ui.components.Hairline
import com.handrail.ui.components.Kicker
import com.handrail.ui.icons.HandrailIcons
import com.handrail.ui.theme.HandrailColors
import com.handrail.ui.theme.HandrailType

/**
 * The screen the design doesn't have: a Conversations row's destination.
 * Drawn as hairline-separated turns in the design's own type system — no
 * chat bubbles, per the deviations table — since a bubble list is exactly
 * the un-styled look this whole port is replacing.
 */
@Composable
fun ThreadDetailScreen(entry: ChatEntry?, onBack: () -> Unit) {
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
                Icon(imageVector = HandrailIcons.ArrowLeft, contentDescription = "Back", tint = HandrailColors.Neutral700, modifier = Modifier.size(20.dp))
            }
            Kicker(text = "Conversation", fontSize = 15.sp, letterSpacing = 0.2.em)
            Spacer(Modifier.width(32.dp))
        }
        Hairline()

        if (entry == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "This conversation is gone.", style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 15.sp), color = HandrailColors.Neutral600)
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
            items(entry.turns) { turn -> TurnRow(turn) }
            if (entry.status == ChatStatus.RUNNING) {
                item { ThinkingRow() }
            }
        }
    }
}

@Composable
private fun ThinkingRow() {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Kicker(text = "Handrail", fontSize = 12.sp, letterSpacing = 0.18.em, color = HandrailColors.Accent700)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Thinking…",
            style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 15.5.sp, lineHeight = 23.sp),
            color = HandrailColors.Neutral600,
        )
    }
}

@Composable
private fun TurnRow(turn: ChatTurn) {
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)) {
        Kicker(
            text = if (turn.role == "user") "You" else "Handrail",
            fontSize = 12.sp,
            letterSpacing = 0.18.em,
            color = if (turn.role == "user") HandrailColors.Neutral600 else HandrailColors.Accent700,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = turn.text,
            style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 15.5.sp, lineHeight = 23.sp),
            color = HandrailColors.Text,
        )
        Spacer(Modifier.height(14.dp))
        Hairline()
    }
}
