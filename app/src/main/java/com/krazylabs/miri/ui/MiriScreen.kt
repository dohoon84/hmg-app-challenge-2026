package com.krazylabs.miri.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.krazylabs.miri.domain.TurnLamps
import com.krazylabs.miri.mvp.F2Draft
import com.krazylabs.miri.mvp.MiriPane
import com.krazylabs.miri.mvp.dateLabel
import com.krazylabs.miri.mvp.timeLabel
import java.time.LocalDate

@Composable
fun MiriRoute(viewModel: MiriViewModel) {
    val ui by viewModel.ui.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
    ) {
        when (ui.pane) {
            MiriPane.ICON -> DrivingIcon()
            MiriPane.F1 -> KindCard(
                onOwn = viewModel::chooseOwnCar,
                onRental = { viewModel.chooseKind(rental = true) },
                onShare = { viewModel.chooseKind(rental = false) },
            )
            MiriPane.F2 -> ReturnCard(
                draft = ui.draft,
                places = ui.places,
                today = LocalDate.now(),
                onOpenDates = viewModel::openDates,
                onDate = viewModel::selectDate,
                onTime = viewModel::selectTime,
                onPlace = viewModel::selectPlace,
                onDone = viewModel::confirmReturn,
            )
            MiriPane.D1, MiriPane.D2, MiriPane.F3, MiriPane.CHECK -> InfoCard(
                card = ui.card,
                onGuide = viewModel::requestReturnGuide,
            )
        }
        if (ui.showScenarioPanel) {
            ScenarioPanel(
                overrides = ui.scenario,
                onChange = viewModel::updateScenario,
                onRoute = viewModel::requestDemoRoute,
                onRoad = viewModel::requestDemoRoad,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        if (ui.simulator) {
            LampRow(
                modifier = Modifier.align(Alignment.BottomStart),
                onLeft = { viewModel.setDemoLamps(TurnLamps(left = true)) },
                onRight = { viewModel.setDemoLamps(TurnLamps(right = true)) },
                onHazard = { viewModel.setDemoLamps(TurnLamps(left = true, right = true)) },
                onOff = { viewModel.setDemoLamps(TurnLamps()) },
            )
        }
    }
}

@Composable
private fun DrivingIcon() {
    val color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(96.dp)) {
            val body = Path().apply {
                moveTo(size.width * 0.28f, size.height * 0.18f)
                lineTo(size.width * 0.72f, size.height * 0.18f)
                lineTo(size.width * 0.84f, size.height * 0.42f)
                lineTo(size.width * 0.84f, size.height * 0.82f)
                lineTo(size.width * 0.16f, size.height * 0.82f)
                lineTo(size.width * 0.16f, size.height * 0.42f)
                close()
            }
            drawPath(body, color)
            drawRoundRect(
                color = color.copy(alpha = 0.25f),
                topLeft = Offset(size.width * 0.30f, size.height * 0.28f),
                size = Size(size.width * 0.40f, size.height * 0.22f),
                cornerRadius = CornerRadius(8f, 8f),
            )
        }
    }
}

@Composable
private fun InfoCard(card: ShownCard?, onGuide: () -> Unit) {
    val face = card ?: return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
    ) {
        PortSketch(face.portName)
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            face.badge?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
            Text(face.title, style = MaterialTheme.typography.headlineMedium)
            face.lines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyLarge)
            }
            if (face.chips.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    face.chips.forEach { chip ->
                        Text(
                            chip,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }
            face.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            face.footer?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            if (face.showGuide) {
                TextButton(onClick = onGuide) { Text("반납 장소로 안내") }
            }
        }
    }
}

@Composable
private fun PortSketch(portName: String?) {
    val body = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.18f)
    val mark = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(220.dp)) {
        drawRoundRect(
            color = body,
            topLeft = Offset(size.width * 0.22f, size.height * 0.08f),
            size = Size(size.width * 0.56f, size.height * 0.84f),
            cornerRadius = CornerRadius(36f, 36f),
        )
        if (portName == null) return@Canvas
        val rear = portName.startsWith("REAR")
        val front = portName.startsWith("FRONT")
        val right = portName.endsWith("RIGHT")
        val left = portName.endsWith("LEFT")
        val cx = when {
            right -> size.width * 0.84f
            left -> size.width * 0.16f
            else -> size.width * 0.50f
        }
        val cy = when {
            rear -> size.height * 0.78f
            front -> size.height * 0.22f
            else -> size.height * 0.50f
        }
        drawCircle(mark, radius = 16f, center = Offset(cx, cy))
    }
}

@Composable
private fun KindCard(onOwn: () -> Unit, onRental: () -> Unit, onShare: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("어떤 차로 타고 계세요?", style = MaterialTheme.typography.headlineMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onOwn) { Text("내 차") }
            Button(onClick = onRental) { Text("렌터카") }
            Button(onClick = onShare) { Text("쉐어카") }
        }
    }
}

@Composable
private fun ReturnCard(
    draft: F2Draft,
    places: List<PlaceChip>,
    today: LocalDate,
    onOpenDates: () -> Unit,
    onDate: (Int) -> Unit,
    onTime: (Int) -> Unit,
    onPlace: (String) -> Unit,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
        Text(F2Draft.TITLE, style = MaterialTheme.typography.headlineMedium)
        TextButton(onClick = onOpenDates) {
            Text(dateLabel(today, draft.dayOffset, draft.epochDay))
        }
        if (draft.dateSheetOpen) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                F2Draft.dateOffsets().forEach { offset ->
                    Button(onClick = { onDate(offset) }) {
                        Text(dateLabel(today, offset))
                    }
                }
            }
        }
        Text(draft.minuteOfDay?.let(::timeLabel) ?: F2Draft.TIME_EMPTY)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            F2Draft.timeOptions().forEach { minute ->
                val selected = draft.minuteOfDay == minute
                if (selected) {
                    Button(onClick = { onTime(minute) }) { Text(timeLabel(minute)) }
                } else {
                    TextButton(onClick = { onTime(minute) }) { Text(timeLabel(minute)) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            places.forEach { place ->
                val selected = draft.placeId == place.id
                if (selected) {
                    Button(onClick = { onPlace(place.id) }) { Text(place.label) }
                } else {
                    TextButton(onClick = { onPlace(place.id) }) { Text(place.label) }
                }
            }
        }
        Button(onClick = onDone, enabled = draft.doneEnabled) { Text(F2Draft.DONE) }
        if (!draft.doneEnabled) {
            Text(F2Draft.DONE_HINT, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.64f))
        }
    }
}

@Composable
private fun LampRow(
    modifier: Modifier,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onHazard: () -> Unit,
    onOff: () -> Unit,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onLeft) { Text("좌") }
        TextButton(onClick = onRight) { Text("우") }
        TextButton(onClick = onHazard) { Text("비상") }
        TextButton(onClick = onOff) { Text("끔") }
    }
}
