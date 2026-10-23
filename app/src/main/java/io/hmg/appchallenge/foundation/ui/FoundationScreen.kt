package io.hmg.appchallenge.foundation.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.hmg.appchallenge.foundation.R
import io.hmg.appchallenge.foundation.domain.AppShell
import io.hmg.appchallenge.foundation.domain.DoorId
import io.hmg.appchallenge.foundation.domain.Motion
import io.hmg.appchallenge.foundation.domain.PanePolicy
import io.hmg.appchallenge.foundation.domain.RangeSource
import io.hmg.appchallenge.foundation.domain.WindowId
import io.hmg.appchallenge.foundation.domain.maskVin
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
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        val twoPanes = PanePolicy.useTwoPanes(
            landscape = landscape,
            widthDp = maxWidth.value.toInt(),
        )
        if (ui.shell == AppShell.REDUCED) {
            DrivingScreen(ui, twoPanes, viewModel)
        } else {
            ParkedScreen(ui, twoPanes, viewModel, onRequestEnergyPermission)
        }
    }
}

@Composable
private fun DrivingScreen(
    ui: FoundationUi,
    twoPanes: Boolean,
    viewModel: FoundationViewModel,
) {
    if (twoPanes) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                DrivingStatus(ui)
            }
            DrivingActions(
                ui,
                viewModel,
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DrivingStatus(ui)
            DrivingActions(
                ui,
                viewModel,
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DrivingStatus(ui: FoundationUi) {
    val clip = clipFor(ui)
    Text(
        text = stringResource(R.string.shell_driving),
        fontSize = 40.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.secondary,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (ui.simulatorEnabled) {
        Text(stringResource(R.string.sim_banner), color = MaterialTheme.colorScheme.secondary)
    }
    Text(
        text = clip(energyLine(ui)),
        fontSize = 56.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    val openDoors = ui.vehicle.doors.values.count { it.open }
    if (openDoors > 0) {
        Text(
            text = clip(stringResource(R.string.door_open_alert)),
            fontSize = 36.sp,
            color = MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    ui.destination?.let { dest ->
        Text(
            text = clip(dest.name),
            fontSize = 28.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = clip(remainLine(ui)),
            fontSize = 22.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    ui.trip?.let { trip ->
        Text(
            text = clip(etaLine(trip.eta, trip.distance)),
            fontSize = 22.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DrivingActions(
    ui: FoundationUi,
    viewModel: FoundationViewModel,
    modifier: Modifier = Modifier,
) {
    val actions = drivingActionList(ui, viewModel)
    BoxWithConstraints(modifier) {
        val columns = drivingActionColumns(
            actionCount = actions.size,
            availableHeightDp = maxHeight.value.toInt(),
            availableWidthDp = maxWidth.value.toInt(),
        )
        val rows = actions.chunked(columns)
        val needed = DRIVING_BUTTON_HEIGHT * rows.size + DRIVING_BUTTON_GAP * (rows.size - 1).coerceAtLeast(0)
        val scroll = if (needed > maxHeight && actions.size > 1) {
            Modifier.verticalScroll(rememberScrollState())
        } else {
            Modifier
        }
        Column(
            modifier = scroll.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(DRIVING_BUTTON_GAP),
        ) {
            rows.forEach { row ->
                val labelStyle = drivingActionLabelStyle(if (row.size > 1) columns else 1)
                if (row.size == 1) {
                    LargeButton(row[0].label, labelStyle = labelStyle, onClick = row[0].onClick)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(DRIVING_BUTTON_GAP),
                    ) {
                        row.forEach { action ->
                            LargeButton(
                                label = action.label,
                                modifier = Modifier.weight(1f),
                                labelStyle = labelStyle,
                                onClick = action.onClick,
                            )
                        }
                        repeat(columns - row.size) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun drivingActionList(ui: FoundationUi, viewModel: FoundationViewModel): List<DrivingAction> {
    val actions = mutableListOf<DrivingAction>()
    if (ui.simulatorEnabled) {
        actions += DrivingAction(stringResource(R.string.action_route_sample)) { viewModel.setSampleDestination() }
        actions += DrivingAction(stringResource(R.string.action_trip_sample)) { viewModel.requestSampleTrip() }
    }
    actions += DrivingAction(stringResource(R.string.action_refresh_dest)) { viewModel.refreshDestination() }
    if (ui.simulatorEnabled) {
        actions += DrivingAction(stringResource(R.string.action_park)) { viewModel.setParked(true) }
        actions += DrivingAction(stringResource(R.string.action_door)) { viewModel.toggleDriverDoor() }
        actions += DrivingAction(stringResource(R.string.action_soc_up)) { viewModel.nudgeSoc(10) }
        actions += DrivingAction(stringResource(R.string.action_soc_down)) { viewModel.nudgeSoc(-10) }
        actions += DrivingAction(stringResource(R.string.action_arrival)) { viewModel.simulateArrival() }
    }
    return actions
}

private data class DrivingAction(val label: String, val onClick: () -> Unit)

/**
 * Keeps every driving control on screen at [buttonHeightDp]. Adds columns when a single
 * stack would be taller than the pane, and never makes a button narrower than [minButtonWidthDp].
 */
internal fun drivingActionColumns(
    actionCount: Int,
    availableHeightDp: Int,
    availableWidthDp: Int,
    buttonHeightDp: Int = 88,
    gapDp: Int = 12,
    minButtonWidthDp: Int = 96,
): Int {
    if (actionCount <= 1) return 1
    val width = availableWidthDp.coerceAtLeast(minButtonWidthDp)
    val maxByWidth = ((width + gapDp) / (minButtonWidthDp + gapDp))
        .coerceAtLeast(1)
        .coerceAtMost(actionCount)
    if (availableHeightDp <= 0) return maxByWidth
    for (columns in 1..maxByWidth) {
        val rows = (actionCount + columns - 1) / columns
        val needed = rows * buttonHeightDp + (rows - 1).coerceAtLeast(0) * gapDp
        if (needed <= availableHeightDp) return columns
    }
    return maxByWidth
}

/**
 * A shared column is about 97dp wide, which clips a 22sp label. Wrap those labels at 18sp.
 * A single full-width button keeps 22sp.
 */
internal data class DrivingLabelStyle(
    val fontSp: Int,
    val maxLines: Int,
    val horizontalPaddingDp: Int,
)

internal fun drivingActionLabelStyle(columns: Int): DrivingLabelStyle =
    if (columns <= 1) {
        DrivingLabelStyle(fontSp = 22, maxLines = 1, horizontalPaddingDp = 24)
    } else {
        DrivingLabelStyle(fontSp = 18, maxLines = 2, horizontalPaddingDp = 8)
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
    Text("VIN ${if (ui.vehicle.vinReadable) maskVin(ui.vehicle.vin) ?: "-" else "읽기 불가"}")
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
        LargeButton(stringResource(R.string.action_energy_permission), onClick = onRequestEnergyPermission)
    }
}

@Composable
private fun NavPane(ui: FoundationUi, viewModel: FoundationViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
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
    if (ui.keyboardAllowed) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(R.string.hint_name)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = latitude,
            onValueChange = { latitude = it },
            label = { Text(stringResource(R.string.hint_lat)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = longitude,
            onValueChange = { longitude = it },
            label = { Text(stringResource(R.string.hint_lon)) },
            modifier = Modifier.fillMaxWidth(),
        )
        ui.formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        LargeButton(stringResource(R.string.action_set_custom)) {
            viewModel.submitDestination(latitude, longitude, name, asTrip = false)
        }
        LargeButton(stringResource(R.string.action_trip_custom)) {
            viewModel.submitDestination(latitude, longitude, name, asTrip = true)
        }
    } else {
        Text(stringResource(R.string.keyboard_blocked))
    }
    LargeButton(stringResource(R.string.action_refresh_dest)) { viewModel.refreshDestination() }
}

@Composable
private fun LargeButton(
    label: String,
    modifier: Modifier = Modifier,
    labelStyle: DrivingLabelStyle = drivingActionLabelStyle(1),
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(DRIVING_BUTTON_HEIGHT),
        contentPadding = if (labelStyle.maxLines > 1) {
            PaddingValues(horizontal = labelStyle.horizontalPaddingDp.dp, vertical = 4.dp)
        } else {
            ButtonDefaults.ContentPadding
        },
    ) {
        Text(
            text = label,
            modifier = Modifier.fillMaxWidth(),
            fontSize = labelStyle.fontSp.sp,
            lineHeight = (labelStyle.fontSp + 4).sp,
            maxLines = labelStyle.maxLines,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun clipFor(ui: FoundationUi): (String) -> String {
    val restricted = ui.driving.requiresDistractionOptimization || ui.shell == AppShell.REDUCED
    val maxLen = ui.driving.maxRestrictedStringLength
    return { text -> shortenForRestriction(text, maxLen, restricted) }
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
private val DRIVING_BUTTON_HEIGHT = 88.dp
private val DRIVING_BUTTON_GAP = 12.dp
