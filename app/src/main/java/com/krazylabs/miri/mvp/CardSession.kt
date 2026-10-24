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
    /** Door-close chimes that arrive on back-to-back ticks count as one event. */
    private var checkChimeAt: Long? = null
    private val toldRoutes = linkedSetOf<String>()
    private val toldD2 = linkedSetOf<String>()
    /** An empty D2 key has no route to store. One show per rental, then [resetRental]. */
    private var toldUnkeyedD2 = false
    private val shownF3 = mutableSetOf<String>()
    /** F3 is recorded as shown when it opens, so the next publish no longer offers it. */
    private var f3Face: CardFace? = null
    /**
     * True after D2 has been shown in this park. A destination that arrives
     * later in the same park changes the key; that key is absorbed and D2
     * does not open again until the car leaves Park.
     */
    private var parkD2Told = false
    /**
     * D2 key last seen in Park. A drive consumes a key only when it differs
     * from this one, which is a destination chosen or changed while moving.
     * That park card is skipped and D2-b remains the driving cue. A charging
     * route set in Park and not yet shown keeps this key and opens at the next Park.
     */
    private var d2KeyInPark = ""
    /**
     * True when F3 was already pending as this park opened. A value applied
     * after the stop has started waits for the next park instead of burning
     * the key on a card the current stop does not keep.
     */
    private var f3ReadyThisPark = false
    private var deferred: CardId? = null
    private var deferFromPrior = false
    private var deferUsed = false
    private var completion = false

    fun onResume() {
        cameraCover = false
    }

    /** A new rental may show D1 and D2 again. */
    fun resetRental() {
        toldRoutes.clear()
        toldD2.clear()
        toldUnkeyedD2 = false
        parkD2Told = false
        d2KeyInPark = ""
        shownF3.clear()
        checkShows = 0
        checkChimeAt = null
        deferred = null
        deferFromPrior = false
        deferUsed = false
        completion = false
        held = null
        voiceStamp = null
    }

    /** D1's 다음 closes the card the way the timer does. */
    fun dismissD1() {
        if (held != CardId.D1) return
        spent = true
        held = null
        voiceStamp = null
    }

    fun f3Shown(): Set<String> = shownF3

    fun step(input: DeckInput): DeckStep {
        val moving = input.gear == GearPosition.DRIVE || input.gear == GearPosition.REVERSE
        if (input.gear != GearPosition.PARK) parkD2Told = false
        if (input.gear == GearPosition.REVERSE) cameraCover = true
        if (moving) {
            val drivingKey = input.d2?.routeKey?.takeIf { it.isNotEmpty() }.orEmpty()
            if (drivingKey.isNotEmpty() && drivingKey != d2KeyInPark) toldD2 += drivingKey
            if (drivingKey.isNotEmpty()) d2KeyInPark = drivingKey
            if (deferFromPrior && held != deferred) {
                deferred = null
                deferUsed = true
            }
            deferFromPrior = deferred != null
            parkOpen = false
            spent = false
            held = null
            f3Face = null
            checkCount = -1
            completion = false
            return DeckStep(MiriPane.ICON, null, VoicePlay.None)
        }
        if (input.gear == GearPosition.PARK) {
            d2KeyInPark = input.d2?.routeKey?.takeIf { it.isNotEmpty() }.orEmpty()
            if (parkD2Told && d2KeyInPark.isNotEmpty()) toldD2 += d2KeyInPark
        }
        if (input.gear != GearPosition.PARK || cameraCover || !input.edgesOpen) {
            return DeckStep(MiriPane.ICON, null, VoicePlay.None)
        }
        if (!parkOpen) {
            parkOpen = true
            spent = false
            held = null
            checkCount = -1
            checkChimeAt = null
            voiceStamp = null
            f3ReadyThisPark = input.f3 != null
        }
        // 새 대여 on C2 is the same park with the kind cleared. Leave the
        // finished check and open F1, as an open F2 already does.
        if (completion && !input.ownCar && input.showIntake && input.kind == null) {
            completion = false
            held = null
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
        // F1 and F2 are the intake. Choosing a kind while parked opens F2
        // without a gear change. Saving the return spends this park.
        if (held == CardId.F1 && (input.ownCar || input.kind != null)) {
            held = null
            voiceStamp = null
        }
        if (held == CardId.F2 && (input.returnSaved || input.ownCar)) {
            held = null
            voiceStamp = null
            spent = true
            return DeckStep(MiriPane.ICON, null, VoicePlay.None)
        }
        // A new rental clears the kind while this park is still F2. Drop the
        // hold so the same stop opens F1 again.
        if (held == CardId.F2 && input.kind == null) {
            held = null
            voiceStamp = null
        }
        if (!input.visible) {
            rememberMiss(input)
            return DeckStep(MiriPane.ICON, null, VoicePlay.None)
        }
        if (held == null) {
            val picked = pick(input)
            if (picked != null) hold(picked, input)
        }
        val id = held ?: return DeckStep(MiriPane.ICON, null, VoicePlay.None)
        if ((id == CardId.D1 || id == CardId.D2) && input.nowMs - heldAt >= input.cardMs) {
            spent = true
            held = null
            return DeckStep(MiriPane.ICON, null, VoicePlay.None)
        }
        val face = face(id, input) ?: run {
            if (id == CardId.CHECK) {
                completion = true
                held = null
                val voice = checkChime(input.nowMs, once("check-done", VoicePlay.Chime))
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
        var closeHandled = false
        if (id == CardId.CHECK) {
            val count = input.checkItems.size
            if (checkCount >= 0 && count < checkCount) {
                voice = checkChime(input.nowMs, VoicePlay.Chime)
                closeHandled = true
            }
            checkCount = count
        }
        if (id == CardId.F3) f3Face = face
        if (voice == VoicePlay.None && !closeHandled) voice = once(stamp(id, face), intended(id, input))
        return DeckStep(face.pane, face, voice)
    }

    private fun hold(id: CardId, input: DeckInput) {
        held = id
        heldAt = input.nowMs
        if (id == CardId.D1) input.d1?.routeId?.let { toldRoutes += it }
        if (id == CardId.D2) rememberD2(input.d2?.routeKey.orEmpty())
        if (id == CardId.F3) input.f3?.let { shownF3 += F3Rules.coveredBy(it.key) }
        if (id == CardId.CHECK) checkShows += 1
        if (id == deferred) {
            deferred = null
            deferUsed = true
        }
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
        // An open door, window, or light is the return check and takes this park.
        // The charge line alone (f4) does not. While it outranked D1, D2, and F3,
        // applying a charge target after the first drive kept every later park on
        // CHECK, so those three cards never appeared.
        if (input.checkItems.isNotEmpty()) return CardId.CHECK
        if (!input.ownCar && input.showIntake && input.kind == null) return CardId.F1
        if (!input.ownCar && input.kind != null && !input.returnSaved) return CardId.F2
        val d2 = input.d2
        if (d2 != null && d2Unseen(d2.routeKey)) return CardId.D2
        if (input.f3 != null && f3ReadyThisPark) return CardId.F3
        val d1 = input.d1
        if (input.edgesOpen && d1 != null && d1.routeId !in toldRoutes && toldRoutes.size < 3) {
            return CardId.D1
        }
        if (input.f4 != null) return CardId.CHECK
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
        } ?: f3Face
        CardId.D1 -> input.d1?.let {
            CardFace(MiriPane.D1, "오늘 길", it.lines)
        }
    }

    private fun intended(id: CardId, input: DeckInput): VoicePlay = when (id) {
        CardId.D1 -> input.d1?.let { VoicePlay.Clips(listOf(it.clip)) } ?: VoicePlay.None
        CardId.D2 -> input.d2?.let { face ->
            if (face.clips.isEmpty()) VoicePlay.None else VoicePlay.Clips(face.clips)
        } ?: VoicePlay.None
        CardId.F3 -> VoicePlay.None
        CardId.F1 -> VoicePlay.None
        CardId.CHECK -> if (input.reshowCheck || checkShows > 1) VoicePlay.Chime else VoicePlay.Clips(listOf("vo_check_done"))
        CardId.F2 -> VoicePlay.None
    }

    private fun stamp(id: CardId, face: CardFace): String = "$id:${face.title}:${face.lines}"

    private fun rememberD2(routeKey: String) {
        parkD2Told = true
        if (routeKey.isEmpty()) toldUnkeyedD2 = true else toldD2 += routeKey
    }

    private fun d2Unseen(routeKey: String): Boolean =
        if (routeKey.isEmpty()) !toldUnkeyedD2 else routeKey !in toldD2

    private fun checkChime(nowMs: Long, voice: VoicePlay): VoicePlay {
        if (voice == VoicePlay.None) return voice
        val last = checkChimeAt
        if (last != null && nowMs - last < CHECK_CHIME_GAP_MS) return VoicePlay.None
        checkChimeAt = nowMs
        return voice
    }

    private fun once(stamp: String, voice: VoicePlay): VoicePlay {
        if (voice == VoicePlay.None || voiceStamp == stamp) return VoicePlay.None
        voiceStamp = stamp
        return voice
    }

    private companion object {
        const val CHECK_CHIME_GAP_MS = 800L
    }
}
