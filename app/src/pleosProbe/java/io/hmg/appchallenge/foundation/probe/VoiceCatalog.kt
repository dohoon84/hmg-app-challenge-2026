package io.hmg.appchallenge.foundation.probe

/**
 * Pure selection rules for the voice probe. Android TTS types stay in [VoiceProbe].
 */
internal data class VoicePreset(
    val id: String,
    val rate: Float,
    val pitch: Float,
)

internal val VOICE_PRESETS = listOf(
    VoicePreset("A", 0.9f, 1.0f),
    VoicePreset("B", 0.9f, 1.1f),
    VoicePreset("C", 0.85f, 1.05f),
    VoicePreset("default", 1.0f, 1.0f),
)

internal val VOICE_SENTENCES = listOf(
    "s1" to "이 차는 휘발유차예요. 주유구는 왼쪽이에요.",
    "s2" to "오늘 길엔 터널이 1곳 있어요.",
)

internal const val MAX_SYNTH_VOICES = 6

internal data class KoreanVoice(
    val engine: String,
    val engineLabel: String,
    val name: String,
    val locale: String,
    val quality: Int,
    val qualityName: String,
    val latency: Int,
    val latencyName: String,
    val networkRequired: Boolean,
    val features: List<String>,
    val fileStem: String = "",
)

internal data class VoiceSelection(
    val chosen: List<KoreanVoice>,
    val skipped: List<KoreanVoice>,
)

internal fun isKoreanVoice(
    language: String,
    languageTag: String,
    voiceName: String,
): Boolean {
    val lang = language.lowercase()
    if (lang == "ko" || lang == "kor") return true
    val tag = languageTag.lowercase().replace('_', '-')
    if (tag == "ko" || tag == "kor" || tag.startsWith("ko-") || tag.startsWith("kor-")) return true
    val name = voiceName.lowercase().replace('_', '-')
    return name.contains("ko-kr") || name.contains("kor-kr") || name.startsWith("ko-") || name.startsWith("kor-")
}

internal fun voiceScaleName(value: Int): String = when (value) {
    500 -> "VERY_HIGH"
    400 -> "HIGH"
    300 -> "NORMAL"
    200 -> "LOW"
    100 -> "VERY_LOW"
    else -> value.toString()
}

internal fun sanitizeVoiceName(name: String): String {
    val cleaned = buildString {
        name.forEach { ch ->
            append(
                when {
                    ch.isLetterOrDigit() || ch == '.' || ch == '-' -> ch
                    else -> '_'
                },
            )
        }
    }.trim('_').replace(Regex("_+"), "_")
    return cleaned.ifEmpty { "voice" }.take(80)
}

internal fun voiceFileName(stem: String, presetId: String, sentenceId: String): String =
    "${stem}_${presetId}_$sentenceId.wav"

internal fun selectVoices(
    voices: List<KoreanVoice>,
    limit: Int = MAX_SYNTH_VOICES,
    defaultEngine: String? = null,
): VoiceSelection {
    val ordered = voices.sortedWith(voiceOrder(defaultEngine))
    val chosen = if (ordered.size > limit) ordered.take(limit) else ordered
    return VoiceSelection(
        chosen = assignFileStems(chosen),
        skipped = ordered.drop(chosen.size),
    )
}

private fun voiceOrder(defaultEngine: String?): Comparator<KoreanVoice> =
    compareByDescending<KoreanVoice> { it.quality }
        .thenBy { it.latency }
        .thenBy { voice -> if (defaultEngine != null && voice.engine == defaultEngine) 0 else 1 }
        .thenBy { it.name }
        .thenBy { it.engine }

private fun assignFileStems(voices: List<KoreanVoice>): List<KoreanVoice> {
    val used = mutableSetOf<String>()
    return voices.map { voice ->
        var stem = sanitizeVoiceName(voice.name)
        if (!used.add(stem)) {
            val engineBit = sanitizeVoiceName(voice.engine.substringAfterLast('.'))
            stem = sanitizeVoiceName("${voice.name}_$engineBit")
            var suffix = 2
            var candidate = stem
            while (!used.add(candidate)) {
                candidate = "${stem}_$suffix"
                suffix += 1
            }
            stem = candidate
        }
        voice.copy(fileStem = stem)
    }
}
