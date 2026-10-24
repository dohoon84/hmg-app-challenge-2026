package com.krazylabs.miri.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.krazylabs.miri.mvp.ScenarioOverrides

@Composable
@Suppress("UNUSED_PARAMETER")
fun ScenarioPanel(
    overrides: ScenarioOverrides,
    onChange: (ScenarioOverrides) -> Unit,
    onRoute: () -> Unit,
    onRoad: () -> Unit,
    onGearBackend: (String) -> Unit = {},
    onOpenFoundation: () -> Unit = {},
    parked: Boolean = true,
    onNewRental: () -> Unit = {},
    playing: Boolean = false,
    onAutoPlay: () -> Unit = {},
    onStopAutoPlay: () -> Unit = {},
    captionsOn: Boolean = false,
    onToggleCaptions: () -> Unit = {},
    modifier: Modifier = Modifier,
) = Unit
