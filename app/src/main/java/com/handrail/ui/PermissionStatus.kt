package com.handrail.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.handrail.perception.HandrailAccessibilityService

/** A live snapshot of the three permissions onboarding asks for and Settings shows. */
data class Permissions(
    val microphone: Boolean,
    val accessibility: Boolean,
    val defaultAssistant: Boolean,
)

/**
 * Reads permission state directly from the system every time — per the
 * design spec, "drive the Settings pills from the live values, not stored
 * flags." No caching here; callers re-snapshot in onResume.
 */
object PermissionStatus {

    fun snapshot(context: Context): Permissions = Permissions(
        microphone = hasMicrophone(context),
        accessibility = hasAccessibility(context),
        defaultAssistant = isDefaultAssistant(context),
    )

    private fun hasMicrophone(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * The connected-service instance is the ground truth CLAUDE.md asks
     * for, but it's briefly null right after the user flips the system
     * toggle and returns to us — before the service has (re)connected. The
     * Settings.Secure list catches that window so the pill doesn't flicker
     * "Off" at exactly the moment the user expects "On".
     */
    private fun hasAccessibility(context: Context): Boolean {
        if (HandrailAccessibilityService.instance != null) return true
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        val expected = "${context.packageName}/${HandrailAccessibilityService::class.java.name}"
        return enabled.split(':').any { it.equals(expected, ignoreCase = true) }
    }

    /**
     * No public API exists for "is this app the default assistant." Best
     * effort via the two Settings.Secure keys different Android versions
     * use; if neither can be read, report false rather than claim a
     * permission we can't verify.
     */
    private fun isDefaultAssistant(context: Context): Boolean {
        val cr = context.contentResolver
        val assistant = runCatching { Settings.Secure.getString(cr, "assistant") }.getOrNull()
        val voiceInteraction = runCatching { Settings.Secure.getString(cr, "voice_interaction_service") }.getOrNull()
        return assistant?.contains(context.packageName) == true || voiceInteraction?.contains(context.packageName) == true
    }

    fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

    /** Absent on some OEM ROMs — callers must wrap the resulting startActivity in try/catch(ActivityNotFoundException) and fall back to ACTION_SETTINGS. */
    fun assistantSettingsIntent(): Intent = Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)

    /** RECORD_AUDIO has no dedicated settings screen — this app-details page is where a permanently-denied permission gets re-granted. */
    fun appDetailsSettingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))
}
