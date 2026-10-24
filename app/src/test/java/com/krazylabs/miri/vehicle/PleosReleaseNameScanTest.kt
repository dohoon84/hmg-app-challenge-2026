package com.krazylabs.miri.vehicle

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors the dex scan in checkPleosReleaseContents. A trailing semicolon is
 * the dex type boundary, so ConnectorProbe must not hit ConnectorProbeText.
 */
class PleosReleaseNameScanTest {
    @Test
    fun identifierBoundarySkipsALongerName() {
        assertFalse(matches("ConnectorProbeText", "ConnectorProbe"))
        assertFalse(matches("Lfoo/PleosConnectorNamesExtra;", "PleosConnectorNames"))
        assertTrue(matches("Lfoo/ConnectorProbe;", "ConnectorProbe"))
        assertTrue(matches("Lfoo/PleosConnectorNames;", "PleosConnectorNames"))
    }

    private fun matches(text: String, name: String): Boolean =
        Regex("(?<![A-Za-z0-9_])${Regex.escape(name)}(?![A-Za-z0-9_])").containsMatchIn(text)
}
