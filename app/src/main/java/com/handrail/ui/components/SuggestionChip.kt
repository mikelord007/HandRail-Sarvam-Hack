package com.handrail.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handrail.ui.theme.HandrailColors
import com.handrail.ui.theme.HandrailType

/** A home-screen suggestion pill ("Book a cab home", "Read this screen to me"). */
@Composable
fun SuggestionChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val borderColor = if (pressed) HandrailColors.Accent else HandrailColors.Divider
    val shape = RoundedCornerShape(100)

    Text(
        text = text,
        modifier = modifier
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
            .padding(horizontal = 15.dp, vertical = 9.dp),
        style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 14.5.sp),
        color = HandrailColors.Neutral800,
    )
}
