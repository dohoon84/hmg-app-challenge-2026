package com.krazylabs.miri.mvp

import com.krazylabs.miri.domain.GearPosition

enum class CardId { CHECK, F1, F2, D2, F3, D1 }

sealed interface VoicePlay {
    data object None : VoicePlay
    data object Chime : VoicePlay
    data class Clips(val keys: List<String>) : VoicePlay
}

data class D1Face(
    val routeId: String,
    val lines: List<String>,
    val clip: String,
)

data class CardFace(
    val pane: MiriPane,
    val title: String,
    val lines: List<String>,
    val chips: List<String> = emptyList(),
    val footer: String? = null,
    val badge: String? = null,
    val note: String? = null,
    val portName: String? = null,
    val showGuide: Boolean = false,
)

data class DeckStep(
    val pane: MiriPane,
    val card: CardFace?,
    val voice: VoicePlay,
)

data class DeckInput(
    val gear: GearPosition,
    val visible: Boolean,
    val edgesOpen: Boolean,
    val ownCar: Boolean,
    val kind: String?,
    val returnSaved: Boolean,
    val showIntake: Boolean,
    val checkItems: List<String>,
    val checkNote: String?,
    val checkTitle: String,
    val reshowCheck: Boolean,
    val f4: String?,
    val d2: D2Face?,
    val f3: F3Face?,
    val d1: D1Face?,
    val nowMs: Long,
    val cardMs: Long,
    val showGuide: Boolean,
)

/**
 * One card per Park stop. Rank is C, then intake, then D2 over F3, then D1.
 * Drive and Reverse close the card and do not dequeue. Reverse keeps the
 * camera cover until [onResume]. A missed card (except D1) waits for one later Park.
 */
class CardSession {
    private var cameraCover = false
    private var parkOpen = false
    private var spent = false
    private var held: CardId? = null
    private var heldAt = 0L
    private var voiceStamp: String? = null
    private var checkCount = -1
    private var checkShows = 0
    private val toldRoutes = linkedSetOf<String>()
    private val shownF3 = mutableSetOf<String>()
    private var deferred: CardId? = null
    private var deferFromPrior = false
    private var deferUsed = false
    private var completion = false

    fun onResume() {
        cameraCover = false
    }

    fun f3Shown(): Set<String> = shownF3

    fun step(input: DeckInput): DeckStep {
        val moving = input.gear == GearPosition.DRIVE || input.gear == GearPosition.REVERSE
        if (input.gear == GearPosition.REVERSE) cameraCover = true
        if (moving) {
            if (deferFromPrior && held != deferred) {
                deferred = null
                deferUsed = true
            }
            deferFromPrior = deferred != null
            parkOpen = false
            spent = false
            held = null
            checkCount = -1
            completion = false
            return DeckStep(MiriPane.ICON, null, VoicePlay.None)
        }
        if (input.gear != GearPosition.PARK || cameraCover) {
            return DeckStep(MiriPane.ICON, null, VoicePlay.None)
        }
        if (!parkOpen) {
            parkOpen = true
            spent = false
            held = null
            checkCount = -1
            voiceStamp = null
        }
        if (completion) {
            return DeckStep(
                MiriPane.CHECK,
                CardFace(MiriPane.CHECK, input.checkTitle, listOf(CheckRules.DONE)),
                VoicePlay.None,
            )
        }
        if (spent) return DeckStep(MiriPane.ICON, null, VoicePlay.None)
        if (!input.visible) {
            rememberMiss(input)
            return DeckStep(MiriPane.ICON, null, VoicePlay.None)
        }
        if (held == null) {
            val picked = pick(input)
            if (picked != null) hold(picked, input)
        }
        val id = held ?: return DeckStep(MiriPane.ICON, null, VoicePlay.None)
        if (id == CardId.D1 && input.nowMs - heldAt >= input.cardMs) {
            spent = true
            held = null
            return DeckStep(MiriPane.ICON, null, VoicePlay.None)
        }
        val face = face(id, input) ?: run {
            if (id == CardId.CHECK) {
                completion = true
                held = null
                val voice = once("check-done", VoicePlay.Chime)
                return DeckStep(
                    MiriPane.CHECK,
                    CardFace(MiriPane.CHECK, input.checkTitle, listOf(CheckRules.DONE)),
                    voice,
                )
            }
            spent = true
            held = null
            return DeckStep(MiriPane.ICON, null, VoicePlay.None)
        }
        var voice: VoicePlay = VoicePlay.None
        if (id == CardId.CHECK) {
            val count = input.checkItems.size
            if (checkCount >= 0 && count < checkCount) voice = VoicePlay.Chime
            checkCount = count
        }
        if (voice == VoicePlay.None) voice = once(stamp(id, face), intended(id, input))
        return DeckStep(face.pane, face, voice)
    }

    private fun hold(id: CardId, input: DeckInput) {
        held = id
        heldAt = input.nowMs
        if (id == CardId.D1) input.d1?.routeId?.let { toldRoutes += it }
        if (id == CardId.F3) input.f3?.let { shownF3 += F3Rules.coveredBy(it.key) }
        if (id == CardId.CHECK) checkShows += 1
        if (id == deferred) {
            deferred = null
            deferUsed = true
        }
        val skipped = input.d1
        if (id != CardId.D1 && skipped != null) toldRoutes += skipped.routeId
    }

    private fun rememberMiss(input: DeckInput) {
        val id = pick(input) ?: return
        if (id != CardId.D1) input.d1?.routeId?.let { toldRoutes += it }
        if (id == CardId.D1) {
            input.d1?.routeId?.let { toldRoutes += it }
            return
        }
        if (deferred == null && !deferUsed) {
            deferred = id
            deferFromPrior = false
        }
    }

    private fun pick(input: DeckInput): CardId? {
        val check = input.checkItems.isNotEmpty() || input.f4 != null
        if (check) return CardId.CHECK
        if (!input.ownCar && input.showIntake && input.kind == null) return CardId.F1
        if (!input.ownCar && input.kind != null && !input.returnSaved) return CardId.F2
        if (input.d2 != null) return CardId.D2
        if (input.f3 != null) return CardId.F3
        val d1 = input.d1
        if (input.edgesOpen && d1 != null && d1.routeId !in toldRoutes && toldRoutes.size < 3) {
            return CardId.D1
        }
        if (deferred != null && !deferUsed && deferredStill(input, deferred)) return deferred
        return null
    }

    private fun deferredStill(input: DeckInput, id: CardId?): Boolean = when (id) {
        CardId.CHECK -> input.checkItems.isNotEmpty() || input.f4 != null
        CardId.F1 -> input.showIntake && input.kind == null
        CardId.F2 -> input.kind != null && !input.returnSaved
        CardId.D2 -> input.d2 != null
        CardId.F3 -> input.f3 != null
        CardId.D1, null -> false
    }

    private fun face(id: CardId, input: DeckInput): CardFace? = when (id) {
        CardId.CHECK -> {
            if (input.checkItems.isEmpty() && input.f4 == null) null
            else CardFace(
                pane = MiriPane.CHECK,
                title = input.checkTitle,
                lines = input.checkItems + listOfNotNull(input.f4),
                footer = CheckRules.FOOTER,
                badge = if (input.reshowCheck || checkShows > 1) CheckRules.badge(input.checkItems.size) else null,
                note = input.checkNote,
            )
        }
        CardId.F1 -> CardFace(MiriPane.F1, "어떤 차로 타고 계세요?", emptyList())
        CardId.F2 -> CardFace(MiriPane.F2, F2Draft.TITLE, emptyList())
        CardId.D2 -> input.d2?.let {
            CardFace(MiriPane.D2, it.title, it.lines, it.chips, portName = it.port?.name)
        }
        CardId.F3 -> input.f3?.let {
            CardFace(MiriPane.F3, "반납 준비", it.lines, showGuide = input.showGuide)
        }
        CardId.D1 -> input.d1?.let {
            CardFace(MiriPane.D1, "오늘 길", it.lines)
        }
    }

    private fun intended(id: CardId, input: DeckInput): VoicePlay = when (id) {
        CardId.D1 -> input.d1?.let { VoicePlay.Clips(listOf(it.clip)) } ?: VoicePlay.None
        CardId.D2 -> input.d2?.let { VoicePlay.Clips(it.clips) } ?: VoicePlay.None
        CardId.F3 -> VoicePlay.None
        CardId.F1 -> VoicePlay.Clips(listOf("vo_brief_intro"))
        CardId.CHECK -> if (input.reshowCheck || checkShows > 1) VoicePlay.Chime else VoicePlay.Clips(listOf("vo_check_done"))
        CardId.F2 -> VoicePlay.None
    }

    private fun stamp(id: CardId, face: CardFace): String = "$id:${face.title}:${face.lines}"

    private fun once(stamp: String, voice: VoicePlay): VoicePlay {
        if (voice == VoicePlay.None || voiceStamp == stamp) return VoicePlay.None
        voiceStamp = stamp
        return voice
    }
}
