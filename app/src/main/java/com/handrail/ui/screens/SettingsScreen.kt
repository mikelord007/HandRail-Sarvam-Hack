package com.handrail.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.handrail.speech.Language
import com.handrail.ui.Permissions
import com.handrail.ui.components.Hairline
import com.handrail.ui.components.HandrailSwitch
import com.handrail.ui.components.Kicker
import com.handrail.ui.components.LanguageTile
import com.handrail.ui.components.Pill
import com.handrail.ui.icons.HandrailIcons
import com.handrail.ui.theme.HandrailColors
import com.handrail.ui.theme.HandrailType

/**
 * The other half of the "quiet half": the two onboarding choices (language,
 * speaker isn't here — see Home/Voice onboarding), the three permissions
 * re-checked live, and the three behaviour toggles.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    languages: List<Language>,
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit,
    permissions: Permissions,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenMicrophoneSettings: () -> Unit,
    onOpenAssistantSettings: () -> Unit,
    narrateEveryStep: Boolean,
    onNarrateToggle: (Boolean) -> Unit,
    speakSlowly: Boolean,
    onSpeakSlowlyToggle: (Boolean) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(32.dp).clickable(onClick = onBack), contentAlignment = Alignment.CenterStart) {
                Icon(imageVector = HandrailIcons.ArrowLeft, contentDescription = "Back", tint = HandrailColors.Neutral700, modifier = Modifier.size(20.dp))
            }
            Kicker(text = "Settings", fontSize = 15.sp, letterSpacing = 0.2.em)
            Spacer(Modifier.width(32.dp))
        }
        Hairline()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 26.dp),
        ) {
            Kicker(text = "Language", fontSize = 12.sp, letterSpacing = 0.18.em, modifier = Modifier.padding(bottom = 10.dp))
            LanguageGrid(languages, selectedLanguage, onLanguageSelected)
            Text(
                text = "Speaking and listening both use ${selectedLanguage.nativeName}.",
                modifier = Modifier.padding(top = 8.dp, bottom = 26.dp),
                style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 13.sp, fontStyle = FontStyle.Italic),
                color = HandrailColors.Neutral600,
            )

            Kicker(text = "Permissions", fontSize = 12.sp, letterSpacing = 0.18.em, modifier = Modifier.padding(bottom = 6.dp))
            Hairline()
            PermissionRow(label = "Accessibility service", on = permissions.accessibility, onClick = onOpenAccessibilitySettings)
            PermissionRow(label = "Microphone", on = permissions.microphone, onClick = onOpenMicrophoneSettings)
            PermissionRow(label = "Default assistant", on = permissions.defaultAssistant, onClick = onOpenAssistantSettings)
            Spacer(Modifier.height(20.dp))

            Kicker(text = "How I behave", fontSize = 12.sp, letterSpacing = 0.18.em, modifier = Modifier.padding(bottom = 6.dp))
            Hairline()
            ToggleRow(
                title = "Narrate every step",
                description = "Say the action before doing it",
                checked = narrateEveryStep,
                onCheckedChange = onNarrateToggle,
            )
            ToggleRow(
                title = "Always hand back before payment",
                description = "Locked on for the demo",
                checked = true,
                onCheckedChange = null,
            )
            ToggleRow(
                title = "Speak slowly",
                description = "Half speed, longer pauses",
                checked = speakSlowly,
                onCheckedChange = onSpeakSlowlyToggle,
            )
        }
    }
}

@Composable
private fun LanguageGrid(languages: List<Language>, selected: Language, onSelected: (Language) -> Unit) {
    val rows = languages.chunked(2)
    Column {
        rows.forEach { pair ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pair.forEach { language ->
                    LanguageTile(
                        language = language,
                        selected = language == selected,
                        onClick = { onSelected(language) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun PermissionRow(label: String, on: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 16.sp), color = HandrailColors.Text)
        Pill(text = if (on) "On" else "Off", on = on)
    }
    Hairline()
}

@Composable
private fun ToggleRow(title: String, description: String, checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?) {
    val rowModifier = if (onCheckedChange != null) {
        Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }
    } else {
        Modifier.fillMaxWidth()
    }
    Row(
        modifier = rowModifier.padding(vertical = 15.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 16.sp), color = HandrailColors.Text)
            Text(
                text = description,
                style = TextStyle(fontFamily = HandrailType.Lora, fontSize = 13.5.sp, fontStyle = FontStyle.Italic),
                color = HandrailColors.Neutral600,
            )
        }
        Box(modifier = if (onCheckedChange == null) Modifier.alpha(0.45f) else Modifier) {
            HandrailSwitch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
    Hairline()
}
