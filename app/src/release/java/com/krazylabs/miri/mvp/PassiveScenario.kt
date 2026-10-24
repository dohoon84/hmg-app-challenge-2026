package com.krazylabs.miri.mvp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Product timings. Debug injection is not part of this build. */
@Singleton
class PassiveScenario @Inject constructor() : ScenarioInput {
    override val overrides: StateFlow<ScenarioOverrides> = MutableStateFlow(ScenarioOverrides())

    override fun update(next: ScenarioOverrides) = Unit
}
