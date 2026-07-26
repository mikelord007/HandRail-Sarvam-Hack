package com.handrail.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handrail.ui.theme.HandrailColors
import com.handrail.ui.theme.HandrailType

/** The ON/OFF permission-status pill used in Settings. */
@Composable
fun Pill(text: String, on: Boolean, modifier: Modifier = Modifier) {
    val ink = if (on) HandrailColors.Accent700 else HandrailColors.Neutral600
    val border = if (on) HandrailColors.Accent else HandrailColors.Neutral400
    Text(
        text = text.uppercase(),
        modifier = modifier
            .border(1.dp, border, RoundedCornerShape(100))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        style = TextStyle(
            fontFamily = HandrailType.Cormorant,
            fontSize = 13.sp,
            letterSpacing = 0.14.em,
        ),
        color = ink,
    )
}
