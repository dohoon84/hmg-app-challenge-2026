package com.krazylabs.miri.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.krazylabs.miri.BuildConfig
import com.krazylabs.miri.domain.GearPosition
import com.krazylabs.miri.domain.TurnLamps
import com.krazylabs.miri.driving.DrivingStateRepository
import com.krazylabs.miri.mvp.CueKind
import com.krazylabs.miri.mvp.DestinationRole
import com.krazylabs.miri.mvp.DriveCueEngine
import com.krazylabs.miri.mvp.DriveCueInput
import com.krazylabs.miri.mvp.F2Draft
import com.krazylabs.miri.mvp.MiriPane
import com.krazylabs.miri.mvp.ReturnForm
import com.krazylabs.miri.mvp.TodayRoad
import com.krazylabs.miri.mvp.arrivalClips
import com.krazylabs.miri.mvp.destinationRole
import com.krazylabs.miri.mvp.miriPane
import com.krazylabs.miri.navi.NavigationRepository
import com.krazylabs.miri.sim.CabinSimulator
import com.krazylabs.miri.speech.ChimePlayer
import com.krazylabs.miri.speech.ClipCuePlayer
import com.krazylabs.miri.speech.SpeechFocus
import com.krazylabs.miri.speech.SpeechScheduler
import com.krazylabs.miri.vehicle.VehicleRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MiriUi(
    val pane: MiriPane = MiriPane.F1,
    val draft: F2Draft = F2Draft(),
    val simulator: Boolean = false,
    val lamps: TurnLamps = TurnLamps(),
)

@HiltViewModel
class MiriViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val vehicleRepository: VehicleRepository,
    private val drivingStateRepository: DrivingStateRepository,
    private val navigationRepository: NavigationRepository,
    private val simulator: CabinSimulator,
    chime: ChimePlayer,
    focus: SpeechFocus,
    scheduler: SpeechScheduler,
) : ViewModel() {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val player = ClipCuePlayer(context, chime, scheduler)
    private val speechFocus = focus
    private val driveCue = DriveCueEngine()
    private val form = ReturnForm()
    private var ownCar = prefs.getBoolean(KEY_OWN, false)
    private var kind = prefs.getString(KEY_KIND, null)
    private var edgesOpen = true
    private var edgesAtMs = 0L
    private val toldRoutes = mutableSetOf<String>()
    private var wasMoving = false
    private val lampOverride = MutableStateFlow<TurnLamps?>(null)

    private val _ui = MutableStateFlow(MiriUi(simulator = simulator.enabled))
    val ui: StateFlow<MiriUi> = _ui.asStateFlow()

    init {
        val minute = prefs.getInt(KEY_MINUTE, -1)
        if (minute >= 0) {
            form.selectTime(minute)
            form.selectDate(prefs.getInt(KEY_DAY, 0))
            form.selectPlace(prefs.getString(KEY_PLACE, F2Draft.PLACE_PICKUP) ?: F2Draft.PLACE_PICKUP)
            form.confirm()
        }
        viewModelScope.launch {
            while (isActive) {
                publish()
                delay(250)
            }
        }
    }

    fun onHostPause() {
        edgesOpen = false
        edgesAtMs = 0L
    }

    fun onHostResume() {
        edgesOpen = false
        edgesAtMs = System.currentTimeMillis() + SETTLE_MS
        vehicleRepository.refresh()
        navigationRepository.refreshDestination()
    }

    fun chooseOwnCar() {
        ownCar = true
        prefs.edit().putBoolean(KEY_OWN, true).remove(KEY_KIND).apply()
        kind = null
        publish()
    }

    fun chooseKind(rental: Boolean) {
        kind = if (rental) KIND_RENTAL else KIND_SHARE
        prefs.edit().putBoolean(KEY_OWN, false).putString(KEY_KIND, kind).apply()
        ownCar = false
        publish()
    }

    fun openDates() {
        form.openDateSheet()
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
            .putInt(KEY_DAY, saved.dayOffset)
            .putString(KEY_PLACE, saved.placeId)
            .apply()
        publish()
    }

    fun setDemoLamps(lamps: TurnLamps?) {
        lampOverride.value = lamps
        publish()
    }

    private fun publish() {
        val now = System.currentTimeMillis()
        if (!edgesOpen && edgesAtMs != 0L && now >= edgesAtMs) edgesOpen = true
        val gear = drivingStateRepository.state.value.gear
        val moving = gear == GearPosition.DRIVE || gear == GearPosition.REVERSE
        if (moving && !wasMoving && form.saved == null) form.closedWithoutConfirm()
        wasMoving = moving
        val lamps = lampOverride.value ?: vehicleRepository.snapshot.value.turnLamps
        val destination = navigationRepository.destination.value
        val road = navigationRepository.road.value
        val role = destination?.name?.let(::destinationRole) ?: DestinationRole.OTHER
        val routeId = road.routeId.ifEmpty {
            destination?.let { "${it.name}@${it.latitude},${it.longitude}" }.orEmpty()
        }
        val cue = driveCue.step(
            DriveCueInput(
                nowMs = now,
                driveGear = gear == GearPosition.DRIVE,
                lamps = lamps,
                navVoice = speechFocus.navigationOrAssistantActive(),
                holdMs = if (BuildConfig.DEBUG) DEMO_HOLD_MS else PRODUCT_HOLD_MS,
                acceptEdges = edgesOpen,
                routeId = routeId,
                d2bEligible = role != DestinationRole.OTHER,
                remainMeters = destination?.remainDistance,
                remainSeconds = destination?.remainTime,
                d2bClips = arrivalClips(role == DestinationRole.CHARGING),
            ),
        )
        when (cue.kind) {
            CueKind.CHIME -> player.chime()
            CueKind.CLIPS -> player.clips(cue.clips)
            CueKind.NONE -> Unit
        }
        if (gear == GearPosition.PARK && road.routing && road.routeId.isNotEmpty()) {
            val key = TodayRoad.voiceKey(road.tolls, road.highways, parked = true)
            if (key != null && road.routeId !in toldRoutes && toldRoutes.size < 3) {
                toldRoutes += road.routeId
                player.clips(listOf(key))
            }
        }
        _ui.value = MiriUi(
            pane = miriPane(gear, ownCar, kind != null, form.saved != null),
            draft = form.draft,
            simulator = simulator.enabled,
            lamps = lamps,
        )
    }

    private companion object {
        const val PREFS = "miri_rental"
        const val KEY_OWN = "own"
        const val KEY_KIND = "kind"
        const val KEY_MINUTE = "minute"
        const val KEY_DAY = "day"
        const val KEY_PLACE = "place"
        const val KIND_RENTAL = "rental"
        const val KIND_SHARE = "share"
        const val SETTLE_MS = 2_500L
        const val DEMO_HOLD_MS = 8_000L
        const val PRODUCT_HOLD_MS = 20_000L
    }
}
