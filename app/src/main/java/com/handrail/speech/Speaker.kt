package com.handrail.speech

/**
 * A Bulbul TTS voice. [id] is the literal Sarvam `speaker` request field.
 *
 * The design names these Meera / Arvind / Pavithra, but those are NOT real
 * Sarvam speaker IDs — verified live against the API, which rejected
 * "meera" and returned its actual roster (anushka, abhilash, manisha, vidya,
 * arya, karun, ...). [displayName] keeps the design's persona name; [id]
 * maps it onto a real speaker confirmed working on `bulbul:v2`.
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
            id = "anushka",
            displayName = "Meera",
            description = "Warm, unhurried — the default",
            waveformBars = listOf(12, 22, 15),
        ),
        Speaker(
            id = "abhilash",
            displayName = "Arvind",
            description = "Low and steady, slower pace",
            waveformBars = listOf(9, 26, 11),
        ),
        Speaker(
            id = "vidya",
            displayName = "Pavithra",
            description = "Bright and quick, for busy screens",
            waveformBars = listOf(16, 18, 24),
        ),
    )

    val DEFAULT = ALL.first { it.id == "anushka" }

    fun byId(id: String): Speaker = ALL.firstOrNull { it.id == id } ?: DEFAULT
}
