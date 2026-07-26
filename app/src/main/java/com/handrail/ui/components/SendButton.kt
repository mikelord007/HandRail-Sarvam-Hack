package com.handrail.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.handrail.R
import com.handrail.ui.icons.HandrailIcons
import com.handrail.ui.theme.HandrailColors

/** The gold-outlined arrow button next to a [HandrailTextField] — shared by Home and Thread Detail's message rows. */
@Composable
fun SendButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .border(1.dp, HandrailColors.Accent, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = HandrailIcons.ArrowRight,
            contentDescription = stringResource(R.string.cd_send),
            tint = HandrailColors.Accent,
            modifier = Modifier.size(18.dp),
        )
    }
}
