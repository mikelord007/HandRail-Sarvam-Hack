package com.handrail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handrail.R
import com.handrail.ui.components.HandbackConfirmButton
import com.handrail.ui.components.HandbackSecondaryButton
import com.handrail.ui.theme.HandrailColors
import com.handrail.ui.theme.HandrailDimens
import com.handrail.ui.theme.HandrailType

/**
 * The hard stop — the safety beat the whole app exists for. [actionLabel]
 * is the REAL blocked button's visible text (e.g. "Pay ₹450"), carried all
 * the way from [com.handrail.actions.IrreversibleActionGuard] through
 * [com.handrail.agent.AgentEvent.Blocked]; nothing here is placeholder copy.
 *
 * [onConfirm] must NOT perform the guarded action — it only dismisses
 * Handrail so the real button underneath becomes tappable again. See
 * [HandbackConfirmButton]'s doc: this is the one solid-gold surface in the
 * app, and the one button Handrail will never press itself.
 */
@Composable
fun HandbackScreen(actionLabel: String, onConfirm: () -> Unit, onDecline: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HandrailColors.OverlayInkFlat),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp)
                .padding(bottom = 20.dp)
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(HandrailDimens.RadiusLg))
                .clip(RoundedCornerShape(HandrailDimens.RadiusLg))
                .background(HandrailColors.Accent100)
                .border(1.dp, HandrailColors.Accent, RoundedCornerShape(HandrailDimens.RadiusLg))
                .padding(horizontal = 22.dp, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(26.dp).border(1.dp, HandrailColors.Accent700, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "!", style = TextStyle(fontFamily = HandrailType.Cormorant, fontSize = 15.sp), color = HandrailColors.Accent700)
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.handback_kicker),
                    style = TextStyle(fontFamily = HandrailType.Cormorant, fontSize = 14.sp, letterSpacing = 0.2.em),
                    color = HandrailColors.Accent700,
                )
            }
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.handback_action_question, actionLabel),
                style = TextStyle(fontFamily = HandrailType.Cormorant, fontSize = 32.sp, lineHeight = 39.sp).merge(HandrailType.TabularFigures),
                color = HandrailColors.Text,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.handback_body),
                style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 16.sp, lineHeight = 27.sp),
                color = HandrailColors.Accent900,
            )
            Spacer(Modifier.height(18.dp))

            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(HandrailColors.Accent300))
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.handback_footer),
                style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 14.5.sp, lineHeight = 23.sp, fontStyle = FontStyle.Italic),
                color = HandrailColors.Accent800,
            )

            Spacer(Modifier.height(18.dp))
            HandbackConfirmButton(label = stringResource(R.string.handback_confirm_label), onClick = onConfirm)
            Spacer(Modifier.height(8.dp))
            HandbackSecondaryButton(label = stringResource(R.string.handback_decline_label), onClick = onDecline)
        }
    }
}
