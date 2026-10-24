package com.krazylabs.miri.ui

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
                today = LocalDate.now(),
                onOpenDates = viewModel::openDates,
                onDate = viewModel::selectDate,
                onTime = viewModel::selectTime,
                onDone = viewModel::confirmReturn,
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
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f)),
        )
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
    today: LocalDate,
    onOpenDates: () -> Unit,
    onDate: (Int) -> Unit,
    onTime: (Int) -> Unit,
    onDone: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.fillMaxWidth()) {
        Text(F2Draft.TITLE, style = MaterialTheme.typography.headlineMedium)
        TextButton(onClick = onOpenDates) {
            Text(dateLabel(today, draft.dayOffset))
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
        Text(if (draft.placeId == F2Draft.PLACE_PICKUP) F2Draft.PICKUP_LABEL else draft.placeId)
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
