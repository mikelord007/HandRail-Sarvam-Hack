package com.handrail.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handrail.ui.theme.HandrailColors

/**
 * The shared onboarding header: "Step N of 6" on the left, "Back" on the
 * right (both same kicker styling), then a hairline. [onBack] is null on the
 * one screen that has no Back — the promise screen.
 *
 * [dividerBottomMargin] defaults to the design's 30dp; the mic screen alone
 * uses 34dp, per spec.
 */
@Composable
fun StepHeader(
    step: Int,
    modifier: Modifier = Modifier,
    total: Int = 6,
    onBack: (() -> Unit)? = null,
    dividerBottomMargin: Dp = 30.dp,
    kickerColor: Color = HandrailColors.Neutral600,
    dividerColor: Color = HandrailColors.Divider,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Kicker(text = "Step $step of $total", fontSize = 13.sp, letterSpacing = 0.18.em, color = kickerColor)
            if (onBack != null) {
                Kicker(
                    text = "Back",
                    fontSize = 13.sp,
                    letterSpacing = 0.18.em,
                    color = kickerColor,
                    modifier = Modifier.clickable(onClick = onBack),
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Hairline(color = dividerColor)
        Spacer(Modifier.height(dividerBottomMargin))
    }
}
