package com.handrail.ui.screens.onboarding

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.handrail.R
import com.handrail.speech.Language
import com.handrail.ui.components.LanguageRow
import com.handrail.ui.components.CtaButton
import com.handrail.ui.components.StepHeader
import com.handrail.ui.theme.HandrailColors
import com.handrail.ui.theme.HandrailType

/** Step 1 of 6 — deliberately before any permission ask: this and Voice are gifts, not requests. */
@Composable
fun LanguageScreen(
    languages: List<Language>,
    selected: Language,
    onSelected: (Language) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 22.dp, start = 30.dp, end = 30.dp, bottom = 26.dp),
    ) {
        StepHeader(step = 1, onBack = onBack)

        Text(
            text = stringResource(R.string.language_screen_title),
            style = TextStyle(fontFamily = HandrailType.Cormorant, fontSize = 34.sp, lineHeight = 39.sp),
            color = HandrailColors.Text,
        )
        Text(
            text = stringResource(R.string.language_screen_body),
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 15.5.sp, lineHeight = 26.sp),
            color = HandrailColors.Neutral700,
        )

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            items(languages, key = { it.code }) { language ->
                LanguageRow(language = language, selected = language == selected, onClick = { onSelected(language) })
            }
        }

        Text(
            text = stringResource(R.string.language_screen_count, languages.size),
            modifier = Modifier.padding(vertical = 12.dp),
            style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 13.5.sp, fontStyle = FontStyle.Italic),
            color = HandrailColors.Neutral600,
        )

        CtaButton(text = stringResource(R.string.continue_label), onClick = onContinue)
    }
}
