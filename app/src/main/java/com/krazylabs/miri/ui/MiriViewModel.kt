package com.krazylabs.miri.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krazylabs.miri.BuildConfig
import com.krazylabs.miri.mvp.CardVoiceGate
import com.krazylabs.miri.domain.GearBackend
import com.krazylabs.miri.domain.GearPosition
import com.krazylabs.miri.domain.PlaceRequest
import com.krazylabs.miri.domain.TripEstimate
import com.krazylabs.miri.domain.TurnLamps
import com.krazylabs.miri.driving.DrivingStateRepository
import com.krazylabs.miri.mvp.CardFace
import com.krazylabs.miri.mvp.CardSession
import com.krazylabs.miri.mvp.CarSpec
import com.krazylabs.miri.mvp.DemoActions
import com.krazylabs.miri.mvp.CheckRules
import com.krazylabs.miri.mvp.CueKind
import com.krazylabs.miri.mvp.D1Face
import com.krazylabs.miri.mvp.D2Rules
import com.krazylabs.miri.mvp.DeckInput
import com.krazylabs.miri.mvp.DestinationRole
import com.krazylabs.miri.mvp.DriveCueEngine
import com.krazylabs.miri.mvp.DriveCueInput
import com.krazylabs.miri.mvp.F2Draft
import com.krazylabs.miri.mvp.F3Face
import com.krazylabs.miri.mvp.F3Rules
import com.krazylabs.miri.mvp.MiriPane
import com.krazylabs.miri.mvp.RearDoorNote
import com.krazylabs.miri.mvp.RentalArm
import com.krazylabs.miri.mvp.RouteKey
import com.krazylabs.miri.mvp.ReturnForm
import com.krazylabs.miri.mvp.ScenarioInput
import com.krazylabs.miri.mvp.ScenarioOverrides
import com.krazylabs.miri.mvp.TodayRoad
import com.krazylabs.miri.mvp.TripOnce
import com.krazylabs.miri.mvp.VoicePlay
import com.krazylabs.miri.mvp.destinationRole
import com.krazylabs.miri.mvp.straightLineTrip
import com.krazylabs.miri.navi.NavigationRepository
import com.krazylabs.miri.sim.CabinSimulator
import com.krazylabs.miri.speech.ChimePlayer
import com.krazylabs.miri.speech.ClipCuePlayer
import com.krazylabs.miri.speech.SpeechEngine
import com.krazylabs.miri.speech.SpeechFocus
import com.krazylabs.miri.speech.SpeechHalt
import com.krazylabs.miri.speech.SpeechScheduler
import com.krazylabs.miri.speech.SpeechUsage
import com.krazylabs.miri.vehicle.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

data class ShownCard(
    val title: String,
    val lines: List<String>,
    val chips: List<String> = emptyList(),
    val footer: String? = null,
    val badge: String? = null,
    val note: String? = null,
    val portName: String? = null,
    val showGuide: Boolean = false,
    /** SOC or fuel percent for the D2 hero. Null when the car has not reported one. */
    val chargePercent: Int? = null,
)

data class PlaceChip(
    val id: String,
    val label: String,
    val caption: String? = null,
    val distance: String? = null,
)

data class MiriUi(
    val pane: MiriPane = MiriPane.F1,
    val draft: F2Draft = F2Draft(),
    val simulator: Boolean = false,
    val lamps: TurnLamps = TurnLamps(),
    val card: ShownCard? = null,
    val places: List<PlaceChip> = listOf(PlaceChip(F2Draft.PLACE_PICKUP, F2Draft.PICKUP_LABEL)),
    val scenario: ScenarioOverrides = ScenarioOverrides(),
    val showScenarioPanel: Boolean = false,
)

@HiltViewModel
class MiriViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val vehicleRepository: VehicleRepository,
    private val drivingStateRepository: DrivingStateRepository,
    private val navigationRepository: NavigationRepository,
    private val simulator: CabinSimulator,
    private val scenarioInput: ScenarioInput,
    chime: ChimePlayer,
    focus: SpeechFocus,
    scheduler: SpeechScheduler,
    tts: SpeechEngine,
    private val demo: DemoActions,
) : ViewModel() {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val player = ClipCuePlayer(context, chime, scheduler, tts, focus)
    private val speechFocus = focus
    private val speechHalt = SpeechHalt()
    private val cardVoice = CardVoiceGate()
    private var ticker: Job? = null
    private val driveCue = DriveCueEngine()
    private val form = ReturnForm()
    private val session = CardSession()
    private val rental = RentalArm()
    private val rearNote = RearDoorNote()
    private val trips = TripOnce()
    private val zone = ZoneId.of("Asia/Seoul")
    private var ownCar = prefs.getBoolean(KEY_OWN, false)
    private var kind = prefs.getString(KEY_KIND, null)
    private var edgesOpen = true
    private var edgesAtMs = 0L
    private var hostVisible = true
    private var wasMoving = false
    private var drove = false
    private var checkReshow = false
    private var demoReturnAt: Long? = null
    private var demoReturnMinutes: Int? = null
    private val lampOverride = MutableStateFlow<TurnLamps?>(null)

    private val _ui = MutableStateFlow(MiriUi(simulator = simulator.enabled, showScenarioPanel = BuildConfig.DEBUG))
    val ui: StateFlow<MiriUi> = _ui.asStateFlow()

    init {
        val minute = prefs.getInt(KEY_MINUTE, -1)
        if (minute >= 0) {
            val today = LocalDate.now(zone)
            val epoch = if (prefs.contains(KEY_EPOCH_DAY)) {
                prefs.getLong(KEY_EPOCH_DAY, today.toEpochDay())
            } else {
                today.plusDays(prefs.getInt(KEY_DAY, 0).toLong()).toEpochDay()
            }
            form.restore(
                epoch,
                minute,
                prefs.getString(KEY_PLACE, F2Draft.PLACE_PICKUP) ?: F2Draft.PLACE_PICKUP,
                today,
            )
        }
        armTicker()
    }

    private fun armTicker() {
        if (ticker?.isActive == true) return
        ticker = viewModelScope.launch {
            while (isActive && hostVisible) {
                publish()
                delay(250)
            }
        }
    }

    fun onHostPause() {
        edgesOpen = false
        edgesAtMs = 0L
        hostVisible = false
        ticker?.cancel()
        ticker = null
        player.stop()
        publish()
    }

    fun onHostResume() {
        hostVisible = true
        edgesOpen = false
        edgesAtMs = System.currentTimeMillis() + SETTLE_MS
        session.onResume()
        vehicleRepository.refresh()
        navigationRepository.refreshRoute()
        armTicker()
    }

    /** The activity is no longer visible. Stop clip and TTS even if pause already did. */
    fun onHostStop() {
        player.stop()
    }

    fun chooseOwnCar() {
        ownCar = true
        prefs.edit().putBoolean(KEY_OWN, true).remove(KEY_KIND).apply()
        kind = null
        publish()
    }

    fun chooseKind(rental: Boolean) {
        kind = if (rental) KIND_RENTAL else KIND_SHARE
        ownCar = false
        form.reset()
        prefs.edit()
            .putBoolean(KEY_OWN, false)
            .putString(KEY_KIND, kind)
            .remove(KEY_MINUTE)
            .apply()
        rememberOrigin()
        publish()
    }

    fun openDates() {
        form.openDateSheet()
        publish()
    }

    fun closeDates() {
        form.closeDateSheet()
        publish()
    }

    fun selectDate(offset: Int) {
        form.selectDate(offset)
        publish()
    }

    fun selectTime(minuteOfDay: Int) {
        form.selectTime(minuteOfDay)
        publish()
    }

    fun selectPlace(placeId: String) {
        form.selectPlace(placeId)
        publish()
    }

    fun confirmReturn() {
        if (!form.confirm()) return
        val saved = form.saved ?: return
        prefs.edit()
            .putInt(KEY_MINUTE, saved.minuteOfDay)
            .putLong(KEY_EPOCH_DAY, saved.epochDay)
            .remove(KEY_DAY)
            .putString(KEY_PLACE, saved.placeId)
            .apply()
        publish()
    }

    fun setDemoLamps(lamps: TurnLamps?) {
        lampOverride.value = lamps
        publish()
    }

    fun updateScenario(next: ScenarioOverrides) {
        scenarioInput.update(next)
        publish()
    }

    /** Debug panel. AAOS is the build default; PLEOS reads Vehicle.getGear(). */
    fun setGearBackend(mode: String) {
        val next = GearBackend.normalize(mode)
        drivingStateRepository.setGearBackend(next)
        vehicleRepository.setGearBackend(next)
        scenarioInput.update(scenarioInput.overrides.value.copy(gearBackend = next))
        publish()
    }

    fun requestDemoRoute() = demo.requestDemoRoute()

    fun requestDemoRoad() = demo.requestDemoRoad()

    fun requestReturnGuide() = demo.requestReturnGuide(returnPlace())

    private fun publish() {
        val now = System.currentTimeMillis()
        if (!edgesOpen && edgesAtMs != 0L && now >= edgesAtMs) edgesOpen = true
        val scenario = scenarioInput.overrides.value
        SpeechUsage.apply(scenario.speechUsage.ifBlank { BuildConfig.SPEECH_USAGE })
        val gear = drivingStateRepository.state.value.gear
        val moving = gear == GearPosition.DRIVE || gear == GearPosition.REVERSE
        if (moving && !wasMoving) {
            if (form.saved == null) form.closedWithoutConfirm()
            if (drove && gear == GearPosition.DRIVE) checkReshow = true
        }
        if (moving) drove = true
        wasMoving = moving
        val snapshot = vehicleRepository.snapshot.value
        rental.observe(
            locked = snapshot.doorLocked,
            occupied = snapshot.driverOccupied,
            parked = gear == GearPosition.PARK,
            nowMs = now,
            thresholdMs = scenario.lockMs,
            force = scenario.armNewRental,
        )
        if (gear == GearPosition.PARK && rental.takeEdge() && !ownCar) {
            rental.reset()
            driveCue.resetRental()
            rearNote.reset()
            session.resetRental()
            kind = null
            form.reset()
            prefs.edit().remove(KEY_KIND).remove(KEY_MINUTE).remove(KEY_EPOCH_DAY).remove(KEY_DAY).putBoolean(KEY_OWN, false).apply()
            if (scenario.armNewRental) scenarioInput.update(scenario.copy(armNewRental = false))
        }
        val lamps = lampOverride.value ?: snapshot.turnLamps
        val destination = navigationRepository.destination.value
        val road = navigationRepository.road.value
        val role = when {
            scenario.pretendCharging || navigationRepository.chargingStation.value -> DestinationRole.CHARGING
            destination != null -> destinationRole(destination.name)
            else -> DestinationRole.OTHER
        }
        val identity = snapshot.identity
        val routeKey = RouteKey.of(destination, road.routeId)
        // Our own guidance-usage playback is in the active configurations.
        // Count it as navigation only when it is someone else.
        val navVoice = speechFocus.navigationOrAssistantActive() && !player.audible
        val d2 = D2Rules.offer(identity, role, specAgrees = CarSpec.agrees(identity), routeKey = routeKey)
        val remainMeters = scenario.remainMeters ?: destination?.remainDistance
        val remainSeconds = scenario.remainSeconds ?: destination?.remainTime
        val cue = driveCue.step(
            DriveCueInput(
                nowMs = now,
                driveGear = gear == GearPosition.DRIVE,
                lamps = lamps,
                navVoice = navVoice,
                holdMs = scenario.turnMs,
                acceptEdges = edgesOpen,
                routeId = routeKey,
                d2bEligible = d2 != null && d2.arrivalClips.isNotEmpty(),
                remainMeters = remainMeters,
                remainSeconds = remainSeconds,
                d2bClips = d2?.arrivalClips ?: emptyList(),
            ),
        )
        val tolls = scenario.pretendTolls ?: road.tolls
        val highways = scenario.pretendHighways ?: road.highways
        val pretendRoad = scenario.pretendTolls != null || scenario.pretendHighways != null
        val routeId = when {
            road.routeId.isNotEmpty() -> road.routeId
            pretendRoad -> "pretend"
            else -> ""
        }
        val guiding = (road.routing || pretendRoad) && routeId.isNotEmpty()
        val d1Clip = TodayRoad.voiceKey(tolls, highways, parked = true)
        val d1 = if (guiding && role == DestinationRole.OTHER && d1Clip != null) {
            D1Face(routeId, d1Lines(tolls, highways), d1Clip)
        } else {
            null
        }
        val returnAt = returnAtMs(now, scenario)
        val minutesLeft = returnAt?.let { (it - now) / 60_000L }
        val f3Key = if (!ownCar && kind != null && minutesLeft != null) {
            F3Rules.dueKey(kind, minutesLeft, session.f3Shown())
        } else {
            null
        }
        val tripKey = tripEventKey(f3Key, scenario)
        val cachedTrip = tripKey?.let { trips.cached(it) }
        if (tripKey != null && trips.begin(tripKey)) launchTrip(tripKey)
        val level = if (identity.evOnly) snapshot.socPercent else snapshot.fuelPercent
        val target = scenario.chargeTargetPercent
        val f3 = if (f3Key != null && returnAt != null) {
            F3Face(
                key = f3Key,
                lines = F3Rules.lines(
                    ev = identity.evOnly,
                    level = level,
                    target = target,
                    returnAtMs = returnAt,
                    nowMs = now,
                    etaSeconds = cachedTrip?.eta,
                    zone = zone,
                ),
            )
        } else {
            null
        }
        val items = CheckRules.items(
            doors = snapshot.doors,
            windows = snapshot.windows,
            cabinOn = snapshot.cabinLightOn,
            reading = snapshot.readingLights,
            lightsUseDoorStandIn = scenario.lightsUseDoorStandIn,
        )
        val step = session.step(
            DeckInput(
                gear = gear,
                visible = hostVisible,
                edgesOpen = edgesOpen,
                ownCar = ownCar,
                kind = kind,
                returnSaved = form.saved != null,
                showIntake = if (snapshot.doorLocked == null) kind == null else rental.primed(),
                checkItems = if (drove && !moving) items else emptyList(),
                checkNote = rearNote.observe(snapshot.doors, moving),
                checkTitle = if (navigationRepository.arrival.value != null) {
                    CheckRules.ARRIVED_TITLE
                } else {
                    CheckRules.TITLE
                },
                reshowCheck = checkReshow,
                f4 = if (drove && !moving) {
                    CheckRules.chargeLine(identity.evOnly, level, target)
                } else {
                    null
                },
                d2 = d2,
                f3 = if (ownCar) null else f3,
                d1 = d1,
                nowMs = now,
                cardMs = scenario.cardMs,
                showGuide = scenario.showReturnGuide,
            ),
        )
        val cardOpen = hostVisible && step.card != null && cue.kind == CueKind.NONE
        val voice = cardVoice.decide(
            offered = if (cardOpen) step.voice else VoicePlay.None,
            cardOpen = cardOpen,
            moving = moving || !hostVisible,
            navSpeaking = navVoice,
            nowMs = now,
        )
        if (speechHalt.shouldStop(hostVisible, gear, navVoice)) player.stop()
        Log.i(
            DECK_TAG,
            "pane=${step.pane} kind=$kind saved=${form.saved != null} drove=$drove " +
                "edges=$edgesOpen lock=${snapshot.doorLocked} d1=${d1?.routeId} " +
                "d2=${d2?.routeKey} f3=${f3?.key} checks=${items.size}",
        )
        if (hostVisible) {
            when (cue.kind) {
                CueKind.CHIME -> player.chime()
                CueKind.CLIPS -> player.clips(cue.clips)
                CueKind.NONE -> play(voice)
            }
        }
        _ui.value = MiriUi(
            pane = step.pane,
            draft = form.draft,
            simulator = simulator.enabled,
            lamps = lamps,
            card = step.card?.toShown()?.let { face ->
                if (step.pane == MiriPane.D2) {
                    val percent = if (identity.evOnly) snapshot.socPercent else snapshot.fuelPercent
                    face.copy(chargePercent = percent)
                } else {
                    face
                }
            },
            places = placeChips(),
            scenario = scenarioInput.overrides.value,
            showScenarioPanel = BuildConfig.DEBUG,
        )
    }

    private fun play(voice: VoicePlay) {
        when (voice) {
            VoicePlay.None -> Unit
            VoicePlay.Chime -> player.chime()
            is VoicePlay.Clips -> player.clips(voice.keys)
        }
    }

    private fun tripEventKey(f3Key: String?, scenario: ScenarioOverrides): String? {
        if (f3Key == null) return null
        val saved = form.saved
        val event = when {
            scenario.returnInMinutes != null -> "demo:${scenario.returnInMinutes}"
            saved != null -> "${saved.epochDay}:${saved.minuteOfDay}:${saved.placeId}"
            else -> return null
        }
        return "$f3Key@$event"
    }

    private fun launchTrip(key: String) {
        val place = returnPlace() ?: return
        val origin = navigationRepository.here.value
        val before = navigationRepository.tripSerial.value
        viewModelScope.launch {
            navigationRepository.requestTrip(place)
            val deadline = System.currentTimeMillis() + TRIP_TIMEOUT_MS
            var found: TripEstimate? = null
            while (isActive && System.currentTimeMillis() < deadline) {
                if (navigationRepository.tripSerial.value != before) {
                    found = navigationRepository.trip.value
                    break
                }
                delay(200)
            }
            val estimate = found ?: origin?.let {
                straightLineTrip(it.latitude, it.longitude, place.latitude, place.longitude)
            }
            if (estimate != null) trips.store(key, estimate)
        }
    }

    private fun returnAtMs(now: Long, scenario: ScenarioOverrides): Long? {
        val minutes = scenario.returnInMinutes
        if (minutes != null) {
            if (demoReturnMinutes != minutes || demoReturnAt == null) {
                demoReturnMinutes = minutes
                demoReturnAt = now + minutes * 60_000L
            }
            return demoReturnAt
        }
        demoReturnMinutes = null
        demoReturnAt = null
        val saved = form.saved ?: return null
        val date = LocalDate.ofEpochDay(saved.epochDay)
        val time = LocalTime.of(saved.minuteOfDay / 60, saved.minuteOfDay % 60)
        return date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    }

    private fun returnPlace(): PlaceRequest? {
        val savedId = form.saved?.placeId ?: form.draft.placeId
        if (savedId != F2Draft.PLACE_PICKUP) {
            return navigationRepository.recentPlaces.value.firstOrNull { it.poiId == savedId }
                ?: navigationRepository.recentPlaces.value.firstOrNull { it.name == savedId }
        }
        val lat = prefs.getString(KEY_LAT, null)?.toDoubleOrNull() ?: return navigationRepository.here.value?.let {
            PlaceRequest(it.latitude, it.longitude, F2Draft.PICKUP_LABEL, it.address)
        }
        val lon = prefs.getString(KEY_LON, null)?.toDoubleOrNull() ?: return null
        return PlaceRequest(lat, lon, F2Draft.PICKUP_LABEL)
    }

    private fun rememberOrigin() {
        val here = navigationRepository.here.value ?: return
        prefs.edit()
            .putString(KEY_LAT, here.latitude.toString())
            .putString(KEY_LON, here.longitude.toString())
            .apply()
    }

    private fun placeChips(): List<PlaceChip> {
        val recents = navigationRepository.recentPlaces.value.take(3).map {
            PlaceChip(it.poiId.ifEmpty { it.name }, it.name)
        }
        return listOf(PlaceChip(F2Draft.PLACE_PICKUP, F2Draft.PICKUP_LABEL)) + recents
    }

    private fun d1Lines(tolls: Int, highways: Int): List<String> {
        val lines = mutableListOf<String>()
        if (tolls > 0) lines += "톨게이트 ${tolls}곳"
        if (highways > 0) lines += "고속도로 진입 ${highways}번"
        return lines
    }

    private fun CardFace.toShown(): ShownCard = ShownCard(
        title = title,
        lines = lines,
        chips = chips,
        footer = footer,
        badge = badge,
        note = note,
        portName = portName,
        showGuide = showGuide,
    )

    private companion object {
        const val PREFS = "miri_rental"
        const val KEY_OWN = "own"
        const val KEY_KIND = "kind"
        const val KEY_MINUTE = "minute"
        const val KEY_DAY = "day"
        const val KEY_EPOCH_DAY = "epoch_day"
        const val KEY_PLACE = "place"
        const val KEY_LAT = "origin_lat"
        const val KEY_LON = "origin_lon"
        const val KIND_RENTAL = "rental"
        const val KIND_SHARE = "share"
        const val SETTLE_MS = 2_500L
        const val TRIP_TIMEOUT_MS = 7_000L
        const val DECK_TAG = "MiriDeck"
    }
}
