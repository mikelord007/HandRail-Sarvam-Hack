package com.handrail.ui.screens.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handrail.R
import com.handrail.ui.components.CtaButton
import com.handrail.ui.components.Hairline
import com.handrail.ui.components.StepHeader
import com.handrail.ui.theme.HandrailColors
import com.handrail.ui.theme.HandrailType

/** Step 4 of 6 — the second required permission. Two Android things happen when this is granted: reading labels, and typing/tapping on the user's behalf. */
@Composable
fun AccessibilityScreen(onOpenSettings: () -> Unit, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 22.dp, start = 30.dp, end = 30.dp, bottom = 26.dp),
    ) {
        StepHeader(step = 4, onBack = onBack)

        Text(
            text = stringResource(R.string.a11y_screen_title),
            style = TextStyle(fontFamily = HandrailType.Cormorant, fontSize = 36.sp, lineHeight = 41.sp),
            color = HandrailColors.Text,
        )
        Text(
            text = stringResource(R.string.a11y_screen_body),
            modifier = Modifier.padding(top = 12.dp, bottom = 22.dp),
            style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 16.5.sp, lineHeight = 28.sp),
            color = HandrailColors.Neutral800,
        )

        Hairline()
        TableRow(text = stringResource(R.string.a11y_row_1))
        TableRow(text = stringResource(R.string.a11y_row_2))
        TableRow(text = stringResource(R.string.a11y_row_3), muted = true)

        Spacer(Modifier.weight(1f))
        CtaButton(text = stringResource(R.string.open_android_settings), onClick = onOpenSettings)
        Text(
            text = stringResource(R.string.a11y_settings_path),
            modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
            style = TextStyle(
                fontFamily = HandrailType.Lora,
                fontSize = 14.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
            ),
            color = HandrailColors.Neutral600,
        )
    }
}

@Composable
private fun TableRow(text: String, muted: Boolean = false) {
    Row(
        modifier = Modifier.padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(text = "—", color = HandrailColors.Accent700, style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 15.5.sp))
        Text(
            text = text,
            style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 15.5.sp, lineHeight = 23.sp),
            color = if (muted) HandrailColors.Neutral700 else HandrailColors.Text,
        )
    }
    Hairline()
}
