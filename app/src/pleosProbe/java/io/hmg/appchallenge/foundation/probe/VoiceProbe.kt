package io.hmg.appchallenge.foundation.probe

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Probe-only platform [TextToSpeech] dump. Writes wav files and does not play them.
 * Does not use [io.hmg.appchallenge.foundation.speech.CabinAnnouncer], audio focus, or the chime.
 */
internal class VoiceProbe(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val log = ProbeLog(appContext) { line ->
        if (Looper.myLooper() == Looper.getMainLooper()) {
            push(line)
        } else {
            main.post { push(line) }
        }
    }

    val lines = mutableStateListOf<String>()
    var running by mutableStateOf(false)
        private set
    var status by mutableStateOf("대기")
        private set
    var directoryText by mutableStateOf("")
        private set

    private val directory: File = appContext.getExternalFilesDir("voice")
        ?: File(appContext.filesDir, "voice")
    private val indexFile = File(directory, "index.json")

    private var generation = 0
    private var tts: TextToSpeech? = null
    private var readyEngine: String? = null
    private var step: ((TextToSpeech?, Int) -> Unit)? = null
    private var timeout: Runnable? = null
    private var expectedUtterance: String? = null
    private var utteranceCallback: ((String, Int?) -> Unit)? = null
    private var utteranceErrorCode: Int? = null

    private val labels = linkedMapOf<String, String>()
    private var engineNames = emptyList<String>()
    private var engineIndex = 0
    private var defaultEnginePackage: String? = null
    private val korean = mutableListOf<KoreanVoice>()
    private var selection = VoiceSelection(emptyList(), emptyList())
    private var jobs = emptyList<SynthJob>()
    private var jobIndex = 0
    private val fileRecords = mutableListOf<JSONObject>()
    private var defaultFields: Map<String, Any?> = emptyMap()

    fun run() {
        main.post { startOnMain() }
    }

    private fun startOnMain() {
        if (running) {
            event("already_running", mapOf("directory" to directory.absolutePath))
            return
        }
        running = true
        status = "엔진 확인 중"
        directory.mkdirs()
        directoryText = directory.absolutePath
        korean.clear()
        fileRecords.clear()
        jobs = emptyList()
        jobIndex = 0
        engineIndex = 0
        labels.clear()
        defaultFields = readDefaultEngines()
        event(
            "voice_directory",
            defaultFields + mapOf(
                "path" to directory.absolutePath,
                "index" to indexFile.absolutePath,
                "external" to (appContext.getExternalFilesDir("voice") != null),
            ),
        )
        discoverEngines()
    }

    private fun discoverEngines() {
        val bootstrap = try {
            TextToSpeech(appContext) { }
        } catch (error: Throwable) {
            event("engines", probeFailure(error))
            finishRun("done")
            return
        }
        val listed = runCatching { bootstrap.engines.orEmpty() }.getOrElse { emptyList() }
        runCatching { bootstrap.shutdown() }
        listed.forEach { info -> labels[info.name] = info.label ?: "" }
        val pmPackages = pmEnginePackages()
        pmPackages.forEach { row ->
            val pkg = row["package"] ?: return@forEach
            if (pkg !in labels) labels[pkg] = row["label"].orEmpty()
        }
        val preferred = listOfNotNull(
            defaultFields["tts.defaultEngine"] as? String,
            defaultFields["tts_default_synth"] as? String,
        ).firstOrNull { it.isNotBlank() }
        defaultEnginePackage = preferred
        val names = (listed.map { it.name } + pmPackages.mapNotNull { it["package"] } + listOfNotNull(preferred))
            .filter { it.isNotBlank() }
            .distinct()
        engineNames = if (preferred != null && preferred in names) {
            listOf(preferred) + names.filter { it != preferred }
        } else {
            names
        }
        event(
            "engines",
            mapOf(
                "defaultEngine" to preferred,
                "getEngines" to listed.map { info ->
                    mapOf("name" to info.name, "label" to (info.label ?: ""))
                },
                "pmEngines" to pmPackages,
                "initOrder" to engineNames,
            ),
        )
        openNextDump()
    }

    private fun openNextDump() {
        if (engineIndex >= engineNames.size) {
            beginSynthesis()
            return
        }
        val name = engineNames[engineIndex]
        status = "엔진 확인 ${engineIndex + 1}/${engineNames.size}"
        open(name) { engine, statusCode ->
            if (engine != null && statusCode == TextToSpeech.SUCCESS) {
                try {
                    dump(engine, name)
                } catch (error: Throwable) {
                    event("engine_dump", probeFailure(error) + mapOf("engine" to name))
                }
            } else {
                event(
                    "engine_init",
                    mapOf("engine" to name, "result" to "failed", "status" to statusCode),
                )
            }
            engineIndex += 1
            openNextDump()
        }
    }

    private fun dump(engine: TextToSpeech, packageName: String) {
        val koreanAvailability = runCatching { engine.isLanguageAvailable(Locale.KOREAN) }.getOrElse { Int.MIN_VALUE }
        val koreaAvailability = runCatching {
            engine.isLanguageAvailable(Locale.forLanguageTag("ko-KR"))
        }.getOrElse { Int.MIN_VALUE }
        val defaultVoice = runCatching { engine.defaultVoice }.getOrNull()
        val before = runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
        val setLanguage = runCatching { engine.setLanguage(Locale.forLanguageTag("ko-KR")) }.getOrElse { Int.MIN_VALUE }
        val after = runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
        val voices = LinkedHashMap<String, Voice>()
        (before + after).forEach { voice -> voices.putIfAbsent(voice.name, voice) }
        val koreanHere = voices.values.filter { voice ->
            isKoreanVoice(
                language = voice.locale?.language.orEmpty(),
                languageTag = voice.locale?.toLanguageTag().orEmpty(),
                voiceName = voice.name.orEmpty(),
            )
        }
        event(
            "engine_ready",
            mapOf(
                "engine" to packageName,
                "label" to labels[packageName].orEmpty(),
                "isLanguageAvailable(Locale.KOREAN)" to koreanAvailability,
                "isLanguageAvailableName" to languageAvailabilityName(koreanAvailability),
                "isLanguageAvailable(ko-KR)" to koreaAvailability,
                "isLanguageAvailableKoKrName" to languageAvailabilityName(koreaAvailability),
                "setLanguage(ko-KR)" to setLanguage,
                "setLanguageName" to languageAvailabilityName(setLanguage),
                "defaultVoice" to defaultVoice?.let { voiceFields(it) },
                "voiceCount" to voices.size,
                "koreanVoiceCount" to koreanHere.size,
            ),
        )
        koreanHere.forEach { voice ->
            if (korean.any { it.engine == packageName && it.name == voice.name }) return@forEach
            val record = toRecord(packageName, voice)
            korean += record
            event("voice", voiceRecordFields(record))
        }
    }

    private fun beginSynthesis() {
        selection = selectVoices(korean.toList(), MAX_SYNTH_VOICES, defaultEnginePackage)
        event(
            "voice_selection",
            mapOf(
                "koreanVoiceCount" to korean.size,
                "synthesizeCount" to selection.chosen.size,
                "chosen" to selection.chosen.map { "${it.engine}/${it.name}" },
                "skipped" to selection.skipped.map { "${it.engine}/${it.name}" },
                "limit" to MAX_SYNTH_VOICES,
            ),
        )
        jobs = selection.chosen.groupBy { it.engine }.values.flatten().flatMap { voice ->
            VOICE_PRESETS.flatMap { preset ->
                VOICE_SENTENCES.map { (sentenceId, text) ->
                    SynthJob(voice, preset, sentenceId, text)
                }
            }
        }
        jobIndex = 0
        fileRecords.clear()
        jobs.forEach { job ->
            fileRecords += fileJson(job, "pending", 0L, null, null, null)
        }
        writeIndex()
        if (jobs.isEmpty()) {
            finishRun("done")
            return
        }
        status = "파일 생성 0/${jobs.size}"
        synthesizeNext()
    }

    private fun synthesizeNext() {
        if (!running) return
        if (jobIndex >= jobs.size) {
            finishRun("done")
            return
        }
        val job = jobs[jobIndex]
        status = "파일 생성 ${jobIndex + 1}/${jobs.size}"
        val opened = tts
        if (opened == null || readyEngine != job.voice.engine) {
            open(job.voice.engine) { engine, statusCode ->
                if (engine == null || statusCode != TextToSpeech.SUCCESS) {
                    skipRestOfEngine(job.voice.engine, "init_failed")
                    return@open
                }
                readyEngine = job.voice.engine
                prepareEngine(engine)
                synthesizeNext()
            }
            return
        }
        synthesizeOne(opened, job)
    }

    private fun prepareEngine(engine: TextToSpeech) {
        runCatching { engine.setLanguage(Locale.forLanguageTag("ko-KR")) }
        engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                main.post { finishUtterance(utteranceId, "success", null) }
            }

            @Deprecated("Required by UtteranceProgressListener")
            override fun onError(utteranceId: String?) {
                main.post { finishUtterance(utteranceId, "error", null) }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                main.post { finishUtterance(utteranceId, "error", errorCode) }
            }
        })
    }

    private fun synthesizeOne(engine: TextToSpeech, job: SynthJob) {
        val voice = runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
            .firstOrNull { it.name == job.voice.name }
        if (voice == null) {
            record("voice_missing", null, null, null, null)
            return
        }
        val setVoiceCode = runCatching { engine.setVoice(voice) }.getOrElse { TextToSpeech.ERROR }
        val rateCode = runCatching { engine.setSpeechRate(job.preset.rate) }.getOrElse { TextToSpeech.ERROR }
        val pitchCode = runCatching { engine.setPitch(job.preset.pitch) }.getOrElse { TextToSpeech.ERROR }
        val file = fileFor(job)
        if (file.exists()) file.delete()
        val utteranceId = "probe-$generation-$jobIndex"
        var completed = false
        awaitUtterance(utteranceId) { result, errorCode ->
            completed = true
            record(result, errorCode, setVoiceCode, rateCode, pitchCode)
        }
        val code = try {
            engine.synthesizeToFile(job.text, Bundle(), file, utteranceId)
        } catch (error: Throwable) {
            event("synthesize_throw", probeFailure(error) + mapOf("path" to file.absolutePath))
            TextToSpeech.ERROR
        }
        if (code != TextToSpeech.SUCCESS && !completed) {
            finishUtterance(utteranceId, "rejected", null)
        }
    }

    private fun skipRestOfEngine(engine: String, result: String) {
        if (jobIndex >= jobs.size || jobs[jobIndex].voice.engine != engine) {
            synthesizeNext()
            return
        }
        record(result, null, null, null, null)
    }

    private fun record(
        result: String,
        errorCode: Int?,
        setVoiceCode: Int?,
        rateCode: Int?,
        pitchCode: Int?,
    ) {
        if (jobIndex >= jobs.size) return
        val job = jobs[jobIndex]
        val file = fileFor(job)
        val bytes = if (file.isFile) file.length() else 0L
        val finalResult = if (result == "success" && bytes <= WAV_HEADER_BYTES) "empty" else result
        fileRecords[jobIndex] = fileJson(job, finalResult, bytes, errorCode, setVoiceCode, rateCode, pitchCode)
        event(
            "synthesize",
            mapOf(
                "result" to finalResult,
                "path" to file.absolutePath,
                "bytes" to bytes,
                "engine" to job.voice.engine,
                "voice" to job.voice.name,
                "preset" to job.preset.id,
                "rate" to job.preset.rate,
                "pitch" to job.preset.pitch,
                "sentence" to job.sentenceId,
                "setVoice" to setVoiceCode,
                "setSpeechRate" to rateCode,
                "setPitch" to pitchCode,
                "errorCode" to errorCode,
            ),
        )
        jobIndex += 1
        writeIndex()
        if (finalResult == "init_failed") {
            skipRestOfEngine(job.voice.engine, "init_failed")
        } else {
            synthesizeNext()
        }
    }

    private fun finishRun(eventName: String) {
        writeIndex()
        event(
            eventName,
            mapOf(
                "directory" to directory.absolutePath,
                "index" to indexFile.absolutePath,
                "files" to fileRecords.size,
                "koreanVoiceCount" to korean.size,
                "synthesizeCount" to selection.chosen.size,
            ),
        )
        shutdownQuietly()
        running = false
        status = "완료"
    }

    private fun open(engine: String, onResult: (TextToSpeech?, Int) -> Unit) {
        shutdownQuietly()
        val token = generation
        val holder = arrayOfNulls<TextToSpeech>(1)
        step = onResult
        val created = try {
            TextToSpeech(
                appContext,
                TextToSpeech.OnInitListener { status ->
                    main.post { deliver(token, holder[0], status) }
                },
                engine,
            )
        } catch (error: Throwable) {
            event("engine_init", probeFailure(error) + mapOf("engine" to engine))
            deliver(token, null, TextToSpeech.ERROR)
            return
        }
        holder[0] = created
        tts = created
        armTimeout(INIT_TIMEOUT_MS) {
            event("engine_timeout", mapOf("engine" to engine, "phase" to "init"))
            deliver(token, holder[0], TextToSpeech.ERROR)
        }
    }

    private fun deliver(token: Int, engine: TextToSpeech?, status: Int) {
        if (token != generation) return
        val callback = step ?: return
        step = null
        clearTimeout()
        callback(engine, status)
    }

    private fun awaitUtterance(id: String, onResult: (String, Int?) -> Unit) {
        expectedUtterance = id
        utteranceErrorCode = null
        utteranceCallback = onResult
        armTimeout(SPEAK_TIMEOUT_MS) {
            event("engine_timeout", mapOf("phase" to "synthesize", "utterance" to id))
            finishUtterance(id, "timeout", null)
        }
    }

    private fun finishUtterance(id: String?, result: String, errorCode: Int?) {
        if (id != null && id != expectedUtterance) return
        val callback = utteranceCallback ?: return
        utteranceCallback = null
        expectedUtterance = null
        clearTimeout()
        callback(result, errorCode ?: utteranceErrorCode)
    }

    private fun shutdownQuietly() {
        generation += 1
        clearTimeout()
        step = null
        utteranceCallback = null
        expectedUtterance = null
        val current = tts
        tts = null
        readyEngine = null
        if (current != null) {
            runCatching { current.stop() }
            runCatching { current.shutdown() }
        }
    }

    private fun armTimeout(delayMs: Long, block: () -> Unit) {
        clearTimeout()
        val task = Runnable { block() }
        timeout = task
        main.postDelayed(task, delayMs)
    }

    private fun clearTimeout() {
        timeout?.let { main.removeCallbacks(it) }
        timeout = null
    }

    private fun fileFor(job: SynthJob): File =
        File(directory, voiceFileName(job.voice.fileStem, job.preset.id, job.sentenceId))

    private fun writeIndex() {
        val root = JSONObject()
        root.put("directory", directory.absolutePath)
        root.put("tts_default_synth", defaultFields["tts_default_synth"])
        root.put("tts.defaultEngine", defaultFields["tts.defaultEngine"])
        root.put("tts.defaultEngineSource", defaultFields["tts.defaultEngineSource"])
        root.put("sentences", JSONObject().apply {
            VOICE_SENTENCES.forEach { (id, text) -> put(id, text) }
        })
        root.put("presets", JSONArray().apply {
            VOICE_PRESETS.forEach { preset ->
                put(
                    JSONObject()
                        .put("id", preset.id)
                        .put("rate", preset.rate.toDouble())
                        .put("pitch", preset.pitch.toDouble()),
                )
            }
        })
        root.put("koreanVoiceCount", korean.size)
        root.put("synthesizedVoices", JSONArray().apply {
            selection.chosen.forEach { put(voiceJson(it)) }
        })
        root.put("skippedVoices", JSONArray().apply {
            selection.skipped.forEach { put(voiceJson(it)) }
        })
        root.put("files", JSONArray().apply { fileRecords.forEach { put(it) } })
        runCatching { indexFile.writeText(root.toString(2)) }.onFailure { error ->
            event("index", probeFailure(error) + mapOf("path" to indexFile.absolutePath))
        }
    }

    private fun readDefaultEngines(): Map<String, Any?> {
        val resolver = appContext.contentResolver
        val synth = runCatching {
            Settings.Secure.getString(resolver, Settings.Secure.TTS_DEFAULT_SYNTH)
        }.getOrNull()
        val dotted = runCatching {
            Settings.Secure.getString(resolver, "tts.defaultEngine")
        }.getOrNull()
        val reflected = runCatching {
            val cls = Class.forName("android.speech.tts.TtsEngines")
            val helper = cls.getConstructor(Context::class.java).newInstance(appContext)
            cls.getMethod("getDefaultEngine").invoke(helper) as String?
        }
        val reflectedValue = reflected.getOrNull()
        val chosen = dotted?.takeIf { it.isNotBlank() } ?: reflectedValue?.takeIf { it.isNotBlank() }
        val source = when {
            !dotted.isNullOrBlank() -> "settings:tts.defaultEngine"
            reflected.isSuccess && !reflectedValue.isNullOrBlank() -> "TtsEngines.getDefaultEngine"
            else -> "unavailable"
        }
        return mapOf(
            "tts_default_synth" to synth,
            "tts.defaultEngine" to chosen,
            "tts.defaultEngineSettings" to dotted,
            "tts.defaultEngineSource" to source,
            "tts.defaultEngineError" to reflected.exceptionOrNull()?.message,
        )
    }

    private fun pmEnginePackages(): List<Map<String, String>> {
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        val infos = runCatching {
            appContext.packageManager.queryIntentServices(intent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrDefault(emptyList()) + runCatching {
            appContext.packageManager.queryIntentServices(intent, 0)
        }.getOrDefault(emptyList())
        val rows = linkedMapOf<String, Map<String, String>>()
        infos.forEach { info ->
            val pkg = info.serviceInfo?.packageName ?: return@forEach
            if (pkg in rows) return@forEach
            val label = runCatching { info.loadLabel(appContext.packageManager).toString() }.getOrDefault("")
            rows[pkg] = mapOf("package" to pkg, "label" to label)
        }
        return rows.values.toList()
    }

    private fun toRecord(packageName: String, voice: Voice): KoreanVoice {
        val features = voice.features?.toList()?.sorted().orEmpty()
        return KoreanVoice(
            engine = packageName,
            engineLabel = labels[packageName].orEmpty(),
            name = voice.name.orEmpty(),
            locale = voice.locale?.toLanguageTag().orEmpty(),
            quality = voice.quality,
            qualityName = voiceScaleName(voice.quality),
            latency = voice.latency,
            latencyName = voiceScaleName(voice.latency),
            networkRequired = voice.isNetworkConnectionRequired,
            features = features,
        )
    }

    private fun voiceFields(voice: Voice): Map<String, Any?> = mapOf(
        "name" to voice.name,
        "locale" to voice.locale?.toLanguageTag(),
        "quality" to voice.quality,
        "qualityName" to voiceScaleName(voice.quality),
        "latency" to voice.latency,
        "latencyName" to voiceScaleName(voice.latency),
        "isNetworkConnectionRequired" to voice.isNetworkConnectionRequired,
        "features" to voice.features?.toList()?.sorted().orEmpty(),
    )

    private fun voiceRecordFields(voice: KoreanVoice): Map<String, Any?> = mapOf(
        "engine" to voice.engine,
        "label" to voice.engineLabel,
        "name" to voice.name,
        "locale" to voice.locale,
        "quality" to voice.quality,
        "qualityName" to voice.qualityName,
        "latency" to voice.latency,
        "latencyName" to voice.latencyName,
        "isNetworkConnectionRequired" to voice.networkRequired,
        "features" to voice.features,
    )

    private fun voiceJson(voice: KoreanVoice): JSONObject = JSONObject()
        .put("engine", voice.engine)
        .put("engineLabel", voice.engineLabel)
        .put("name", voice.name)
        .put("locale", voice.locale)
        .put("quality", voice.quality)
        .put("qualityName", voice.qualityName)
        .put("latency", voice.latency)
        .put("latencyName", voice.latencyName)
        .put("networkRequired", voice.networkRequired)
        .put("features", JSONArray(voice.features))
        .put("fileStem", voice.fileStem)

    private fun fileJson(
        job: SynthJob,
        result: String,
        bytes: Long,
        errorCode: Int?,
        setVoiceCode: Int?,
        rateCode: Int?,
        pitchCode: Int? = null,
    ): JSONObject {
        val file = fileFor(job)
        return JSONObject()
            .put("file", file.name)
            .put("path", file.absolutePath)
            .put("result", result)
            .put("bytes", bytes)
            .put("engine", job.voice.engine)
            .put("voice", job.voice.name)
            .put("preset", job.preset.id)
            .put("rate", job.preset.rate.toDouble())
            .put("pitch", job.preset.pitch.toDouble())
            .put("sentence", job.sentenceId)
            .put("text", job.text)
            .put("setVoice", setVoiceCode ?: JSONObject.NULL)
            .put("setSpeechRate", rateCode ?: JSONObject.NULL)
            .put("setPitch", pitchCode ?: JSONObject.NULL)
            .put("errorCode", errorCode ?: JSONObject.NULL)
    }

    private fun event(name: String, fields: Map<String, Any?> = emptyMap()) {
        log.write(TAG, name, bg = false, fields = fields)
    }

    private fun push(line: String) {
        lines.add(0, line)
        while (lines.size > UI_LINES) {
            lines.removeAt(lines.lastIndex)
        }
    }

    private fun languageAvailabilityName(value: Int): String = when (value) {
        TextToSpeech.LANG_AVAILABLE -> "LANG_AVAILABLE"
        TextToSpeech.LANG_COUNTRY_AVAILABLE -> "LANG_COUNTRY_AVAILABLE"
        TextToSpeech.LANG_COUNTRY_VAR_AVAILABLE -> "LANG_COUNTRY_VAR_AVAILABLE"
        TextToSpeech.LANG_MISSING_DATA -> "LANG_MISSING_DATA"
        TextToSpeech.LANG_NOT_SUPPORTED -> "LANG_NOT_SUPPORTED"
        else -> "UNKNOWN_$value"
    }

    private data class SynthJob(
        val voice: KoreanVoice,
        val preset: VoicePreset,
        val sentenceId: String,
        val text: String,
    )

    companion object {
        const val TAG = "PROBE_VOICE"
        private const val INIT_TIMEOUT_MS = 12_000L
        private const val SPEAK_TIMEOUT_MS = 30_000L
        private const val UI_LINES = 80
        private const val WAV_HEADER_BYTES = 44L

        @Volatile
        private var current: VoiceProbe? = null

        fun obtain(context: Context): VoiceProbe {
            current?.let { return it }
            return synchronized(this) {
                current ?: VoiceProbe(context.applicationContext).also { current = it }
            }
        }
    }
}
