package com.handrail.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handrail.ui.components.Kicker
import com.handrail.ui.theme.HandrailColors
import com.handrail.ui.theme.HandrailDimens
import com.handrail.ui.theme.HandrailType
import com.handrail.ui.theme.SetStatusBarAppearance

/**
 * Step 6 of 6 — the only dark screen in the product, existing purely to set
 * an expectation before first use: Handrail never spends the user's money.
 * This is the contract the hand-back screen later honours.
 */
@Composable
fun PromiseScreen(onUnderstood: () -> Unit) {
    SetStatusBarAppearance(lightIcons = true)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HandrailColors.Neutral900)
            .padding(top = 22.dp, start = 30.dp, end = 30.dp, bottom = 26.dp),
    ) {
        Kicker(text = "Step 6 of 6", fontSize = 13.sp, letterSpacing = 0.18.em, color = HandrailColors.Accent300)
        Spacer(Modifier.height(14.dp))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HandrailColors.OnDarkRule))
        Spacer(Modifier.height(48.dp))

        Kicker(
            text = "My promise",
            fontSize = 22.sp,
            letterSpacing = 0.14.em,
            color = HandrailColors.Accent300,
            modifier = Modifier.padding(bottom = 18.dp),
        )
        Text(
            text = "I never spend your money.",
            style = TextStyle(fontFamily = HandrailType.Cormorant, fontWeight = FontWeight.W400, fontSize = 40.sp, lineHeight = 47.sp),
            color = HandrailColors.Neutral100,
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = buildAnnotatedString {
                append("I can open apps, fill forms and choose your ride. But before any payment, any send, any confirm — I stop, I say out loud what is about to happen, and ")
                withStyle(SpanStyle(color = HandrailColors.Accent300)) { append("you") }
                append(" make the last tap.")
            },
            style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 17.sp, lineHeight = 30.sp),
            color = HandrailColors.Neutral300,
        )

        Spacer(Modifier.weight(1f))
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HandrailColors.OnDarkRule))
        Spacer(Modifier.height(22.dp))

        DarkCta(text = "I understand — let's go", onClick = onUnderstood)
    }
}

@Composable
private fun DarkCta(text: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val shape = RoundedCornerShape(HandrailDimens.RadiusMd)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(shape)
            .background(if (pressed) HandrailColors.OnDarkHover else Color.Transparent)
            .border(1.dp, HandrailColors.Accent300, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.uppercase(),
            style = TextStyle(fontFamily = HandrailType.Cormorant, fontSize = 19.sp, letterSpacing = 0.08.em),
            color = HandrailColors.Accent300,
        )
    }
}
