package com.krazylabs.miri.mvp

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Debug-only timings and injected distances. Release builds do not contain this class. */
@Singleton
class DemoScenarioStore @Inject constructor() : ScenarioInput {
    private val _overrides = MutableStateFlow(
        ScenarioOverrides(
            lockMs = 10_000L,
            cardMs = 6_000L,
            turnMs = 8_000L,
        ),
    )
    override val overrides: StateFlow<ScenarioOverrides> = _overrides

    override fun update(next: ScenarioOverrides) {
        _overrides.value = next
    }
}
