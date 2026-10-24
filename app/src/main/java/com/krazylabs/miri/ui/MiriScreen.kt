package com.krazylabs.miri.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import com.krazylabs.miri.R
import com.krazylabs.miri.domain.TurnLamps
import com.krazylabs.miri.mvp.MiriPane
import com.krazylabs.miri.ui.design.D1RoadScreen
import com.krazylabs.miri.ui.design.D2ChargeScreen
import com.krazylabs.miri.ui.design.F1KindScreen
import com.krazylabs.miri.ui.design.F2ReturnScreen
import com.krazylabs.miri.ui.design.C1CheckScreen
import com.krazylabs.miri.ui.design.C2DoneScreen
import com.krazylabs.miri.ui.design.DrivingMark
import com.krazylabs.miri.ui.design.F3ReturnScreen
import com.krazylabs.miri.ui.design.F4ChecklistScreen
import com.krazylabs.miri.ui.design.SystemWallClock
import com.krazylabs.miri.ui.design.WallClock
import com.krazylabs.miri.ui.design.quietClick
import com.krazylabs.miri.ui.theme.AstaSans
import com.krazylabs.miri.ui.theme.IviTheme

@Composable
fun MiriRoute(
    viewModel: MiriViewModel,
    clock: WallClock = SystemWallClock,
    onOpenFoundation: () -> Unit = {},
) {
    val ui by viewModel.ui.collectAsState()
    val dark = isSystemInDarkTheme()
    var demoOpen by rememberSaveable { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(ui.showScenarioPanel) {
                if (!ui.showScenarioPanel) return@pointerInput
                detectTapGestures(onLongPress = { demoOpen = !demoOpen })
            },
    ) {
        when (ui.pane) {
            MiriPane.ICON -> DrivingMark(dark)
            MiriPane.F1 -> F1KindScreen(
                highlighted = null,
                onOwn = viewModel::chooseOwnCar,
                onRental = { viewModel.chooseKind(rental = true) },
                onShare = { viewModel.chooseKind(rental = false) },
                dark = dark,
            )
            MiriPane.F2 -> F2ReturnScreen(
                draft = ui.draft,
                places = ui.places,
                clock = clock,
                onOpenDates = viewModel::openDates,
                onDate = viewModel::selectDate,
                onTime = viewModel::selectTime,
                onPlace = viewModel::selectPlace,
                onDone = viewModel::confirmReturn,
                onCloseDates = viewModel::closeDates,
                searchEnabled = ui.placeSearchEnabled,
                placeSheetOpen = ui.placeSheetOpen,
                bookmarks = ui.bookmarks,
                sheetRecents = ui.sheetRecents,
                onOpenSearch = viewModel::openPlaceSheet,
                onCloseSearch = viewModel::closePlaceSheet,
            )
            MiriPane.D1 -> D1RoadScreen(ui.card, dark, onDone = viewModel::dismissCard)
            MiriPane.D2 -> D2ChargeScreen(ui.card, dark)
            MiriPane.F3 -> F3ReturnScreen(ui.card, dark, onGuide = viewModel::requestReturnGuide)
            MiriPane.CHECK -> CheckPane(ui.card, dark)
        }
        if (ui.showScenarioPanel && demoOpen) {
            DemoDock(
                onClose = { demoOpen = false },
                modifier = Modifier.align(Alignment.TopEnd),
            ) {
                ScenarioPanel(
                    overrides = ui.scenario,
                    onChange = viewModel::updateScenario,
                    onRoute = viewModel::requestDemoRoute,
                    onRoad = viewModel::requestDemoRoad,
                    onGearBackend = viewModel::setGearBackend,
                    onOpenFoundation = onOpenFoundation,
                )
                if (ui.simulator) {
                    LampRow(
                        onLeft = { viewModel.setDemoLamps(TurnLamps(left = true)) },
                        onRight = { viewModel.setDemoLamps(TurnLamps(right = true)) },
                        onHazard = { viewModel.setDemoLamps(TurnLamps(left = true, right = true)) },
                        onOff = { viewModel.setDemoLamps(TurnLamps()) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CheckPane(card: ShownCard?, dark: Boolean) {
    val title = card?.title.orEmpty()
    val done = card?.lines?.any { it.contains("점검 끝") } == true
    when {
        done -> C2DoneScreen(card, dark)
        title.contains("도착") -> F4ChecklistScreen(card, dark)
        else -> C1CheckScreen(card, dark)
    }
}

/**
 * Debug-only dock. Hidden until a long-press on the canvas. No collapsed chip,
 * so the panel never sits on a footer line.
 */
@Composable
private fun DemoDock(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = IviTheme.colors
    Column(
        modifier
            .padding(16.dp)
            .widthIn(max = 560.dp)
            .background(colors.track, RoundedCornerShape(24.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.End,
    ) {
        content()
        Text(
            "데모 닫기",
            modifier = Modifier
                .background(colors.canvas, RoundedCornerShape(16.dp))
                .quietClick(onClick = onClose)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            color = colors.textTertiary,
            fontFamily = AstaSans,
            fontSize = 16.sp,
        )
    }
}

@Composable
private fun LampRow(
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onHazard: () -> Unit,
    onOff: () -> Unit,
) {
    val colors = IviTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("좌" to onLeft, "우" to onRight, "비상" to onHazard, "끔" to onOff).forEach { (label, action) ->
            Text(
                label,
                modifier = Modifier
                    .quietClick(onClick = action)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = colors.textSecondary,
                fontFamily = AstaSans,
                fontSize = 16.sp,
            )
        }
    }
}
