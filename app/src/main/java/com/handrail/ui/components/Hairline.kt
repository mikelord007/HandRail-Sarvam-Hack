package com.handrail.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.handrail.ui.theme.HandrailColors

/** A single 1dp rule. Hairlines carry structure in this design system — used everywhere a border would be in a denser one. */
@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = HandrailColors.Divider) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(color))
}
