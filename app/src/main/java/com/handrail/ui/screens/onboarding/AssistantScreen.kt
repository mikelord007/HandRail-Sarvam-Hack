package com.handrail.ui.screens.onboarding

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handrail.ui.components.CtaButton
import com.handrail.ui.components.GhostButton
import com.handrail.ui.components.StepHeader
import com.handrail.ui.theme.HandrailColors
import com.handrail.ui.theme.HandrailType

/** Step 5 of 6 — skippable. Sets Handrail as the system assistant so a long-press home reaches it from anywhere. */
@Composable
fun AssistantScreen(onSetAsAssistant: () -> Unit, onNotNow: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 22.dp, start = 30.dp, end = 30.dp, bottom = 26.dp),
    ) {
        StepHeader(step = 5, onBack = onBack)

        Text(
            text = "Hold the home button for me",
            style = TextStyle(fontFamily = HandrailType.Cormorant, fontSize = 36.sp, lineHeight = 41.sp),
            color = HandrailColors.Text,
        )
        Text(
            text = "Make Handrail your assistant, and a long press on home brings me up over whatever you're doing.",
            modifier = Modifier.padding(top = 12.dp, bottom = 26.dp),
            style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 16.5.sp, lineHeight = 28.sp),
            color = HandrailColors.Neutral800,
        )

        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(width = 150.dp, height = 6.dp)
                    .background(HandrailColors.Neutral400, RoundedCornerShape(3.dp)),
            )
            BreathingRing()
        }

        Spacer(Modifier.weight(1f))
        CtaButton(text = "Set as assistant", onClick = onSetAsAssistant)
        GhostButton(text = "Not now", onClick = onNotNow)
    }
}

@Composable
private fun BreathingRing() {
    val transition = rememberInfiniteTransition(label = "assistant_breathe")
    val scale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scale",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1300, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "alpha",
    )
    Box(
        modifier = Modifier
            .size(56.dp)
            .graphicsLayer(scaleX = scale, scaleY = scale, alpha = alpha)
            .border(1.dp, HandrailColors.Accent, CircleShape),
    )
}
