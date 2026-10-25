package com.krazylabs.miri.ui

/**
 * Scales the product layer down from the top while a caption band is showing.
 * The band stays at the bottom of the full canvas, and the card content,
 * including C1's footer, is drawn above it. A zero reserve leaves the layout
 * at 1:1.
 */
fun captionInsetScale(heightPx: Float, reservePx: Float): Float {
    if (heightPx <= 0f || reservePx <= 0f || reservePx >= heightPx) return 1f
    return (heightPx - reservePx) / heightPx
}

const val CAPTION_RESERVE_DP = 88f
