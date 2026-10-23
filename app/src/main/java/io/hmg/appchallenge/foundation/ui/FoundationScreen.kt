package io.hmg.appchallenge.foundation.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.hmg.appchallenge.foundation.R
import io.hmg.appchallenge.foundation.domain.AppShell
import io.hmg.appchallenge.foundation.domain.DoorId
import io.hmg.appchallenge.foundation.domain.Motion
import io.hmg.appchallenge.foundation.domain.PanePolicy
import io.hmg.appchallenge.foundation.domain.RangeSource
import io.hmg.appchallenge.foundation.domain.SamplePlaces
import io.hmg.appchallenge.foundation.domain.WindowId
import io.hmg.appchallenge.foundation.domain.shortenForRestriction
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FoundationRoute(
    viewModel: FoundationViewModel,
    onRequestEnergyPermission: () -> Unit,
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val twoPanes = PanePolicy.useTwoPanes(
        landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
        widthDp = configuration.screenWidthDp,
    )
    if (ui.shell == AppShell.REDUCED) {
        DrivingScreen(ui, twoPanes, viewModel)
    } else {
        ParkedScreen(ui, twoPanes, viewModel, onRequestEnergyPermission)
    }
}

@Composable
private fun DrivingScreen(
    ui: FoundationUi,
    twoPanes: Boolean,
    viewModel: FoundationViewModel,
) {
    val restricted = ui.driving.requiresDistractionOptimization
    val maxLen = ui.driving.maxRestrictedStringLength
    val energy = energyLine(ui)
    val openDoors = ui.vehicle.doors.values.count { it.open }
    val dest = ui.destination?.name?.let { shortenForRestriction(it, maxLen, restricted) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.shell_driving),
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.secondary,
        )
        if (ui.simulatorEnabled) {
            Text(stringResource(R.string.sim_banner), color = MaterialTheme.colorScheme.secondary)
        }
        Text(text = energy, fontSize = 56.sp, fontWeight = FontWeight.Bold)
        if (openDoors > 0) {
            Text(
                text = stringResource(R.string.door_open_alert),
                fontSize = 36.sp,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold,
            )
        }
        if (dest != null) {
            Text(text = dest, fontSize = 28.sp)
            Text(text = remainLine(ui), fontSize = 22.sp)
        }
        ui.trip?.let { trip ->
            Text(text = etaLine(trip.eta, trip.distance), fontSize = 22.sp)
        }
        if (twoPanes && ui.simulatorEnabled) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    DrivingActions(viewModel)
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SimulatorActions(viewModel)
                }
            }
        } else {
            DrivingActions(viewModel)
            if (ui.simulatorEnabled) SimulatorActions(viewModel)
        }
    }
}

@Composable
private fun DrivingActions(viewModel: FoundationViewModel) {
    LargeButton(stringResource(R.string.action_route_sample)) {
        viewModel.setDestination(SamplePlaces.seoulStation)
    }
    LargeButton(stringResource(R.string.action_trip_sample)) {
        viewModel.requestTrip(SamplePlaces.seoulStation)
    }
    LargeButton(stringResource(R.string.action_refresh_dest)) {
        viewModel.refreshDestination()
    }
}

@Composable
private fun SimulatorActions(viewModel: FoundationViewModel) {
    LargeButton(stringResource(R.string.action_park)) { viewModel.setParked(true) }
    LargeButton(stringResource(R.string.action_door)) { viewModel.toggleDriverDoor() }
    LargeButton(stringResource(R.string.action_soc_up)) { viewModel.nudgeSoc(10) }
    LargeButton(stringResource(R.string.action_soc_down)) { viewModel.nudgeSoc(-10) }
    LargeButton(stringResource(R.string.action_arrival)) { viewModel.simulateArrival() }
}

@Composable
private fun ParkedScreen(
    ui: FoundationUi,
    twoPanes: Boolean,
    viewModel: FoundationViewModel,
    onRequestEnergyPermission: () -> Unit,
) {
    val vehiclePane: @Composable () -> Unit = { VehiclePane(ui, viewModel, onRequestEnergyPermission) }
    val navPane: @Composable () -> Unit = { NavPane(ui, viewModel) }
    if (twoPanes) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { vehiclePane() }
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) { navPane() }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            vehiclePane()
            navPane()
        }
    }
}

@Composable
private fun VehiclePane(
    ui: FoundationUi,
    viewModel: FoundationViewModel,
    onRequestEnergyPermission: () -> Unit,
) {
    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
    Text(motionLabel(ui.driving.motion), style = MaterialTheme.typography.titleLarge)
    if (ui.simulatorEnabled) {
        Text(stringResource(R.string.sim_banner), color = MaterialTheme.colorScheme.secondary)
    }
    Text("차량 ${ui.vehicleLink.name} · 기어 ${ui.driving.gear.name} (${ui.driving.gearRaw ?: "-"})")
    Text("UX 제한 ${if (ui.driving.requiresDistractionOptimization) "필요" else "없음"} · 기어 속성 ${if (ui.driving.gearAvailable) "있음" else "없음"}")
    Text(stringResource(R.string.section_vehicle), style = MaterialTheme.typography.titleMedium)
    Text(energyLine(ui))
    Text("연료 ${percent(ui.vehicle.fuelPercent, ui.vehicle.fuelReadable)}")
    Text("적산 ${odometer(ui)}")
    Text("주행가능거리 ${range(ui)}")
    Text("VIN ${if (ui.vehicle.vinReadable) ui.vehicle.vin ?: "-" else "읽기 불가"}")
    ui.vehicle.lastError?.let { Text("오류 $it", color = MaterialTheme.colorScheme.error) }
    DoorId.entries.forEach { id ->
        val panel = ui.vehicle.doors[id] ?: return@forEach
        if (!panel.readable && panel.position == null) return@forEach
        Text("${doorLabel(id)} ${if (!panel.readable) "지원 안 함" else if (panel.open) "열림" else "닫힘"} (${panel.position ?: "-"})")
    }
    WindowId.entries.forEach { id ->
        val panel = ui.vehicle.windows[id] ?: return@forEach
        if (!panel.readable) return@forEach
        if (panel.open) Text("창문 ${windowLabel(id)} 열림")
    }
    if (ui.cachedAtEpochMs > 0L) {
        Text("로컬 저장 SOC ${ui.cachedSoc ?: "-"} · ${clock.format(Date(ui.cachedAtEpochMs))}")
    }
    if (ui.simulatorEnabled) {
        Text(stringResource(R.string.section_sim), style = MaterialTheme.typography.titleMedium)
        LargeButton(stringResource(R.string.action_drive)) { viewModel.setParked(false) }
        LargeButton(stringResource(R.string.action_park)) { viewModel.setParked(true) }
        LargeButton(stringResource(R.string.action_door)) { viewModel.toggleDriverDoor() }
        LargeButton(stringResource(R.string.action_soc_up)) { viewModel.nudgeSoc(10) }
        LargeButton(stringResource(R.string.action_soc_down)) { viewModel.nudgeSoc(-10) }
        LargeButton(stringResource(R.string.action_arrival)) { viewModel.simulateArrival() }
    } else {
        LargeButton(stringResource(R.string.action_energy_permission), onRequestEnergyPermission)
    }
}

@Composable
private fun NavPane(ui: FoundationUi, viewModel: FoundationViewModel) {
    var name by remember { mutableStateOf(SamplePlaces.seoulStation.name) }
    var latitude by remember { mutableStateOf(SamplePlaces.seoulStation.latitude.toString()) }
    var longitude by remember { mutableStateOf(SamplePlaces.seoulStation.longitude.toString()) }
    Text(stringResource(R.string.section_nav), style = MaterialTheme.typography.titleMedium)
    Text("내비 ${ui.navLink.name}")
    ui.navStatus?.let { Text(it) }
    ui.destination?.let { dest ->
        Text("목적지 ${dest.name}")
        Text(remainLine(ui))
    }
    ui.trip?.let { trip ->
        Text(etaLine(trip.eta, trip.distance))
        Text("도착 SOC ${trip.estimatedSocAtDestPercent}% · 에너지 ${trip.energyConsumption}")
    }
    ui.arrival?.let { arrival ->
        Text("도착 ${arrival.name} · 거리 ${arrival.distance} · 시간 ${arrival.duration}")
    }
    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.hint_name)) }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = latitude, onValueChange = { latitude = it }, label = { Text(stringResource(R.string.hint_lat)) }, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(value = longitude, onValueChange = { longitude = it }, label = { Text(stringResource(R.string.hint_lon)) }, modifier = Modifier.fillMaxWidth())
    ui.formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    LargeButton(stringResource(R.string.action_set_custom)) {
        viewModel.submitDestination(latitude, longitude, name, asTrip = false)
    }
    LargeButton(stringResource(R.string.action_trip_custom)) {
        viewModel.submitDestination(latitude, longitude, name, asTrip = true)
    }
    LargeButton(stringResource(R.string.action_refresh_dest)) { viewModel.refreshDestination() }
}

@Composable
private fun LargeButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(88.dp),
    ) {
        Text(text = label, fontSize = 22.sp)
    }
}

private fun motionLabel(motion: Motion): String = when (motion) {
    Motion.PARKED -> "정차"
    Motion.DRIVING -> "주행 중"
    Motion.UNKNOWN -> "상태 확인 중"
}

private fun energyLine(ui: FoundationUi): String {
    val vehicle = ui.vehicle
    return when {
        vehicle.socReadable -> "SOC ${vehicle.socPercent ?: "-"}%"
        vehicle.fuelReadable -> "연료 ${vehicle.fuelPercent ?: "-"}%"
        else -> "에너지 정보 없음"
    }
}

private fun percent(value: Int?, readable: Boolean): String =
    if (!readable) "읽기 불가" else "${value ?: "-"}%"

private fun odometer(ui: FoundationUi): String =
    if (!ui.vehicle.odometerReadable) "읽기 불가" else "${ui.vehicle.odometerKm ?: "-"} km"

private fun range(ui: FoundationUi): String = when (ui.vehicle.rangeSource) {
    RangeSource.UNAVAILABLE -> "SDK 미제공"
    RangeSource.AAOS_RANGE_REMAINING, RangeSource.SIMULATED -> {
        val meters = ui.vehicle.rangeMeters
        if (meters == null) "없음" else String.format(Locale.KOREA, "%.1f km (%s)", meters / 1000f, ui.vehicle.rangeSource.name)
    }
}

private fun remainLine(ui: FoundationUi): String {
    val dest = ui.destination ?: return ""
    return "잔여 ${dest.remainDistance} · ${dest.remainTime} (원시값)"
}

private fun etaLine(eta: Int, distance: Int): String = "ETA $eta · 거리 $distance (원시값)"

private fun doorLabel(id: DoorId): String = when (id) {
    DoorId.ROW_1_LEFT -> "운전석"
    DoorId.ROW_1_RIGHT -> "조수석"
    DoorId.ROW_2_LEFT -> "2열 왼쪽"
    DoorId.ROW_2_RIGHT -> "2열 오른쪽"
    DoorId.ROW_3_LEFT -> "3열 왼쪽"
    DoorId.ROW_3_RIGHT -> "3열 오른쪽"
    DoorId.HOOD -> "후드"
    DoorId.REAR -> "트렁크"
}

private fun windowLabel(id: WindowId): String = when (id) {
    WindowId.ROW_1_LEFT -> "운전석"
    WindowId.ROW_1_RIGHT -> "조수석"
    WindowId.ROW_2_LEFT -> "2열 왼쪽"
    WindowId.ROW_2_RIGHT -> "2열 오른쪽"
    WindowId.FRONT_WINDSHIELD -> "전면"
    WindowId.REAR_WINDSHIELD -> "후면"
    WindowId.ROOF_TOP_1 -> "루프 1"
    WindowId.ROOF_TOP_2 -> "루프 2"
}

private val clock = SimpleDateFormat("HH:mm:ss", Locale.KOREA)
