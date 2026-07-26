package com.handrail.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handrail.speech.Language
import com.handrail.ui.theme.HandrailColors
import com.handrail.ui.theme.HandrailDimens
import com.handrail.ui.theme.HandrailType

/** One cell of the Settings 2-column language grid — same data as [LanguageRow], different (compact, bordered) presentation. */
@Composable
fun LanguageTile(language: Language, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val ring = if (selected) HandrailColors.Accent else HandrailColors.Divider
    val ink = if (selected) HandrailColors.Accent700 else HandrailColors.Text

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .border(1.dp, ring, RoundedCornerShape(HandrailDimens.RadiusMd))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.Start,
    ) {
        Text(
            text = language.nativeName,
            style = TextStyle(fontFamily = HandrailType.Cormorant, fontSize = 19.sp, lineHeight = 21.sp),
            color = ink,
        )
        Text(
            text = language.latinName,
            style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 11.5.sp),
            color = HandrailColors.Neutral600,
        )
    }
}
