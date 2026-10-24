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
    modifier: Modifier = Modifier,
) = Unit
