package io.hmg.appchallenge.foundation.session

/**
 * Spike switch for the pleosProbe activity only.
 * Stays false in release and in the normal debug builds, so [CabinSession]
 * still releases NaviHelper when the process backgrounds.
 * The probe screen sets this from its background toggle. Nothing else writes it.
 */
internal object ProbeNavigationHold {
    @Volatile
    var keepRegistered: Boolean = false
}
