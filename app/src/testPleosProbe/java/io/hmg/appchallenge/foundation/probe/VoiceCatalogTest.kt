package io.hmg.appchallenge.foundation.probe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCatalogTest {
    @Test
    fun presetsAndSentencesMatchTheListeningCheck() {
        assertEquals(listOf("A", "B", "C", "default"), VOICE_PRESETS.map { it.id })
        assertEquals(0.9f, VOICE_PRESETS[0].rate)
        assertEquals(1.0f, VOICE_PRESETS[0].pitch)
        assertEquals(0.9f, VOICE_PRESETS[1].rate)
        assertEquals(1.1f, VOICE_PRESETS[1].pitch)
        assertEquals(0.85f, VOICE_PRESETS[2].rate)
        assertEquals(1.05f, VOICE_PRESETS[2].pitch)
        assertEquals(1.0f, VOICE_PRESETS[3].rate)
        assertEquals(1.0f, VOICE_PRESETS[3].pitch)
        assertEquals(
            "s1" to "이 차는 휘발유차예요. 주유구는 왼쪽이에요.",
            VOICE_SENTENCES[0],
        )
        assertEquals(
            "s2" to "오늘 길엔 터널이 1곳 있어요.",
            VOICE_SENTENCES[1],
        )
    }

    @Test
    fun koreanFilterKeepsKoKrAndKor() {
        assertTrue(isKoreanVoice("ko", "ko-KR", "anything"))
        assertTrue(isKoreanVoice("kor", "kor-KR", "anything"))
        assertTrue(isKoreanVoice("", "", "ko-kr-x-ism-local"))
        assertFalse(isKoreanVoice("en", "en-US", "en-us-x-sfg-local"))
        assertFalse(isKoreanVoice("ja", "ja-JP", "ja-jp-x-local"))
    }

    @Test
    fun moreThanSixVoicesKeepsTheHighestQuality() {
        val voices = (1..7).map { index ->
            sample(name = "ko-kr-$index", quality = index * 10, latency = 100)
        }
        val selection = selectVoices(voices, limit = 6, defaultEngine = null)
        assertEquals(6, selection.chosen.size)
        assertEquals(listOf("ko-kr-7", "ko-kr-6", "ko-kr-5", "ko-kr-4", "ko-kr-3", "ko-kr-2"), selection.chosen.map { it.name })
        assertEquals(listOf("ko-kr-1"), selection.skipped.map { it.name })
    }

    @Test
    fun fileNamesUseSanitizedVoicePresetAndSentence() {
        val chosen = selectVoices(listOf(sample("ko-kr-x-ism-local")), limit = 6, defaultEngine = null).chosen.single()
        assertEquals("ko-kr-x-ism-local", chosen.fileStem)
        val names = VOICE_PRESETS.flatMap { preset ->
            VOICE_SENTENCES.map { (id, _) -> voiceFileName(chosen.fileStem, preset.id, id) }
        }
        assertEquals(
            listOf(
                "ko-kr-x-ism-local_A_s1.wav",
                "ko-kr-x-ism-local_A_s2.wav",
                "ko-kr-x-ism-local_B_s1.wav",
                "ko-kr-x-ism-local_B_s2.wav",
                "ko-kr-x-ism-local_C_s1.wav",
                "ko-kr-x-ism-local_C_s2.wav",
                "ko-kr-x-ism-local_default_s1.wav",
                "ko-kr-x-ism-local_default_s2.wav",
            ),
            names,
        )
    }

    @Test
    fun duplicateSanitizedNamesGetAnEngineSuffix() {
        val voices = listOf(
            sample("Same Voice", engine = "com.a.tts"),
            sample("Same Voice", engine = "com.b.engine"),
        )
        val stems = selectVoices(voices, limit = 6, defaultEngine = null).chosen.map { it.fileStem }
        assertEquals(listOf("Same_Voice", "Same_Voice_engine"), stems)
    }

    private fun sample(
        name: String,
        engine: String = "com.example.tts",
        quality: Int = 400,
        latency: Int = 200,
    ) = KoreanVoice(
        engine = engine,
        engineLabel = engine,
        name = name,
        locale = "ko-KR",
        quality = quality,
        qualityName = voiceScaleName(quality),
        latency = latency,
        latencyName = voiceScaleName(latency),
        networkRequired = false,
        features = emptyList(),
    )
}
