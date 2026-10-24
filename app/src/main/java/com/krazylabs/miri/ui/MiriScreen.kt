package com.krazylabs.miri.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.krazylabs.miri.ui.design.HeroTitle
import com.krazylabs.miri.ui.design.ScreenCanvas
import com.krazylabs.miri.ui.design.SystemWallClock
import com.krazylabs.miri.ui.design.WallClock
import com.krazylabs.miri.ui.design.quietClick
import com.krazylabs.miri.ui.theme.AstaSans
import com.krazylabs.miri.ui.theme.IviTheme

@Composable
fun MiriRoute(viewModel: MiriViewModel, clock: WallClock = SystemWallClock) {
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
            )
            MiriPane.D1 -> D1RoadScreen(ui.card, dark)
            MiriPane.D2 -> D2ChargeScreen(ui.card, dark)
            MiriPane.F3, MiriPane.CHECK -> FaceScreen(
                card = ui.card,
                onGuide = viewModel::requestReturnGuide,
            )
        }
        if (ui.showScenarioPanel) {
            DemoDock(
                open = demoOpen,
                onToggle = { demoOpen = !demoOpen },
                modifier = Modifier.align(Alignment.BottomEnd),
            ) {
                ScenarioPanel(
                    overrides = ui.scenario,
                    onChange = viewModel::updateScenario,
                    onRoute = viewModel::requestDemoRoute,
                    onRoad = viewModel::requestDemoRoad,
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
private fun DrivingMark(dark: Boolean) {
    ScreenCanvas {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(
                painter = painterResource(if (dark) R.drawable.miri_car_q34_dark else R.drawable.miri_car_q34_light),
                contentDescription = null,
                modifier = Modifier.size(280.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

@Composable
private fun FaceScreen(card: ShownCard?, onGuide: () -> Unit) {
    val face = card ?: return
    val colors = IviTheme.colors
    ScreenCanvas {
        Column(
            Modifier
                .fillMaxSize()
                .padding(48.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            face.badge?.let {
                Text(it, color = colors.textTertiary, fontFamily = AstaSans, fontSize = 20.sp)
            }
            HeroTitle(face.title)
            face.lines.forEach { line ->
                Text(line, color = colors.textPrimary, fontFamily = AstaSans, fontSize = 28.sp, lineHeight = 40.sp)
            }
            face.note?.let {
                Text(it, color = colors.textSecondary, fontFamily = AstaSans, fontSize = 22.sp, lineHeight = 32.sp)
            }
            face.footer?.let {
                Text(it, color = colors.textTertiary, fontFamily = AstaSans, fontSize = 20.sp)
            }
            if (face.showGuide) {
                Text(
                    "반납 장소로 안내",
                    modifier = Modifier.quietClick(onClick = onGuide).padding(vertical = 12.dp),
                    color = colors.accent,
                    fontFamily = AstaSans,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                )
            }
        }
    }
}

/**
 * Debug-only dock. Collapsed to a small control. A long-press on the canvas opens it too.
 * The open card stays in the corner and does not cover the frame.
 */
@Composable
private fun DemoDock(
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val colors = IviTheme.colors
    Column(modifier.padding(20.dp), horizontalAlignment = Alignment.End) {
        if (open) {
            Column(
                Modifier
                    .widthIn(max = 460.dp)
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState())
                    .background(colors.track, RoundedCornerShape(24.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                content()
            }
        }
        Text(
            if (open) "데모 닫기" else "데모",
            modifier = Modifier
                .padding(top = 8.dp)
                .background(colors.track, RoundedCornerShape(16.dp))
                .quietClick(onClick = onToggle)
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
