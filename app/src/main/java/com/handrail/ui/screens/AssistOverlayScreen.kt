package com.handrail.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handrail.ui.components.VoiceState
import com.handrail.ui.components.VoiceStateIndicator
import com.handrail.ui.icons.HandrailIcons
import com.handrail.ui.theme.HandrailColors
import com.handrail.ui.theme.HandrailType

/**
 * The translucent overlay itself — a caption track over whatever app is
 * running underneath, not a chat window. This composable draws ONLY the
 * bottom scrim and the caption block; the app "underneath" is genuinely
 * transparent here (unlike the design prototype's hatch-pattern stand-in),
 * because [com.handrail.ui.AssistActivity]'s window is translucent.
 *
 * Fixed height rather than wrap-content: a window that resizes on every
 * narration-line change is a visible jump. See AssistActivity's window setup.
 */
@Composable
fun AssistOverlayScreen(
    voiceState: VoiceState,
    narrationLine: String,
    stepLabel: String,
    onClose: () -> Unit,
    onStop: () -> Unit,
    /** Non-null only while a spoken ask_user answer is pending — the design has no visible mic control, so the whole caption area doubles as one. */
    onTapToAnswer: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp)
            .background(
                Brush.verticalGradient(
                    0f to HandrailColors.OverlayInkStrong,
                    0.46f to HandrailColors.OverlayInkSoft,
                    1f to Color.Transparent,
                ),
            )
            .then(if (onTapToAnswer != null) Modifier.clickable(onClick = onTapToAnswer) else Modifier),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 26.dp, vertical = 26.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                VoiceStateIndicator(state = voiceState, markSize = 44.dp, onDark = true)
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier.size(40.dp).clickable(onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(imageVector = HandrailIcons.Close, contentDescription = "Close", tint = HandrailColors.Neutral300, modifier = Modifier.size(20.dp))
                }
            }
            Spacer(Modifier.height(16.dp))

            AnimatedContent(
                targetState = narrationLine,
                transitionSpec = {
                    (slideInVertically(animationSpec = tween(400)) { it / 10 } + fadeIn(tween(400))) togetherWith
                        fadeOut(tween(100))
                },
                label = "narration_line",
            ) { line ->
                Text(
                    text = line,
                    modifier = Modifier.heightIn(min = 118.dp),
                    style = TextStyle(fontFamily = HandrailType.Cormorant, fontSize = 30.sp, lineHeight = 39.sp),
                    color = HandrailColors.Neutral100,
                )
            }

            Spacer(Modifier.height(14.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HandrailColors.OnDarkRuleStrong))
            Spacer(Modifier.height(14.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = stepLabel, style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 14.sp), color = HandrailColors.Neutral400)
                Text(
                    text = "Stop",
                    modifier = Modifier.clickable(onClick = onStop),
                    style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 14.sp, textDecoration = TextDecoration.Underline),
                    color = HandrailColors.Neutral300,
                )
            }
        }
    }
}
