package com.handrail.speech

/**
 * A Bulbul TTS voice. [id] is the literal Sarvam `speaker` request field.
 *
 * The design names these Meera / Arvind / Pavithra, but those are NOT real
 * Sarvam speaker IDs. On `bulbul:v2` these were live-verified as anushka,
 * abhilash, vidya. Moved to `bulbul:v3` for its larger, more natural voice
 * roster (per Sarvam docs); v3 has a different roster than v2 (its own
 * default speaker is "shubh", not "anushka"), so the IDs below (priya,
 * anand, kavya) are chosen from v3's documented roster by name/gender match
 * to each persona, NOT re-verified live by listening — do that on a device
 * before the demo, the same way the v2 mapping originally was.
 */
data class Speaker(
    val id: String,
    val displayName: String,
    val description: String,
    /** Static waveform "fingerprint" bar heights (dp) — decorative, not driven by audio. */
    val waveformBars: List<Int>,
)

object Speakers {
    val ALL = listOf(
        Speaker(
            id = "priya",
            displayName = "Meera",
            description = "Warm, unhurried — the default",
            waveformBars = listOf(12, 22, 15),
        ),
        Speaker(
            id = "anand",
            displayName = "Arvind",
            description = "Low and steady, slower pace",
            waveformBars = listOf(9, 26, 11),
        ),
        Speaker(
            id = "kavya",
            displayName = "Pavithra",
            description = "Bright and quick, for busy screens",
            waveformBars = listOf(16, 18, 24),
        ),
    )

    val DEFAULT = ALL.first { it.id == "priya" }

    fun byId(id: String): Speaker = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
