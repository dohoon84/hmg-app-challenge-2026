package com.krazylabs.miri.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionInsetTest {
    @Test
    fun captionsLeaveTheCheckFooterClear() {
        val height = 1210f
        val reserve = CAPTION_RESERVE_DP
        val footer = 1128f
        assertTrue(footer > height - reserve)
        val scale = captionInsetScale(height, reserve)
        assertTrue(footer * scale + 48f < height - reserve)
        assertEquals(1f, captionInsetScale(height, 0f), 0f)
        assertEquals(1f, captionInsetScale(0f, reserve), 0f)
    }
}
