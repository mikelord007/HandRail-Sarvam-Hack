package com.handrail.ui.screens.onboarding

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handrail.R
import com.handrail.ui.components.CtaButton
import com.handrail.ui.components.Kicker
import com.handrail.ui.icons.HandrailIcons
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
            Image(
                imageVector = HandrailIcons.Logo,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
            )
            Spacer(Modifier.height(26.dp))
            Text(
                text = stringResource(R.string.app_name),
                style = TextStyle(fontFamily = HandrailType.Cormorant, fontSize = 46.sp, lineHeight = 48.sp),
                color = HandrailColors.Text,
            )
            Kicker(
                text = stringResource(R.string.welcome_kicker),
                fontSize = 15.sp,
                letterSpacing = 0.15.em,
                modifier = Modifier.padding(top = 8.dp),
            )
            Text(
                text = stringResource(R.string.welcome_body),
                modifier = Modifier.padding(top = 26.dp),
                style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 17.sp, lineHeight = 30.sp),
                color = HandrailColors.Neutral800,
            )
        }

        CtaButton(text = stringResource(R.string.begin), onClick = onBegin)
        Text(
            text = stringResource(R.string.welcome_footer),
            modifier = Modifier
                .padding(top = 14.dp)
                .fillMaxWidth(),
            style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 14.sp, textAlign = TextAlign.Center),
            color = HandrailColors.Neutral600,
        )
    }
}
