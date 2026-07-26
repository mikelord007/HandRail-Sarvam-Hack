package com.handrail.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handrail.ui.components.CtaButton
import com.handrail.ui.components.Kicker
import com.handrail.ui.theme.HandrailColors
import com.handrail.ui.theme.HandrailType

/** First launch. One sentence, one button — the user often isn't the person who installed the app. */
@Composable
fun WelcomeScreen(onBegin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 30.dp, end = 30.dp, bottom = 26.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .border(1.dp, HandrailColors.Accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 2.dp, height = 44.dp)
                        .background(HandrailColors.Accent),
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(
                text = "Handrail",
                style = TextStyle(fontFamily = HandrailType.Cormorant, fontSize = 46.sp, lineHeight = 48.sp),
                color = HandrailColors.Text,
            )
            Kicker(
                text = "Hold on. I'll walk you through.",
                fontSize = 15.sp,
                letterSpacing = 0.15.em,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = "Tell me what you want to do, in your language. I read the screen aloud, or I do the tapping for you — and I say every step before I take it.",
                modifier = Modifier.padding(top = 26.dp),
                style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 17.sp, lineHeight = 30.sp),
                color = HandrailColors.Neutral800,
            )
        }

        CtaButton(text = "Begin", onClick = onBegin)
        Text(
            text = "Takes about a minute · four permissions",
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth(),
            style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 14.sp, textAlign = TextAlign.Center),
            color = HandrailColors.Neutral600,
        )
    }
}
