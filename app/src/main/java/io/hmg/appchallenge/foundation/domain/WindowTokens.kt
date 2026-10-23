package io.hmg.appchallenge.foundation.domain

/**
 * Connect-S application window. Portrait is the default activity size.
 * Landscape is the two-window width. Values are dp, matching 160dpi where 1dp = 1px.
 * The driving view is a system region. This activity stays a normal window and does not cover it.
 */
object WindowTokens {
    const val PORTRAIT_WIDTH_DP = 840
    const val LANDSCAPE_WIDTH_DP = 1700
    const val APPLICATION_HEIGHT_DP = 1184
    const val DRIVING_VIEW_WIDTH_DP = 840
    const val DRIVING_VIEW_HEIGHT_DP = 1280
    const val DRAWS_IN_NORMAL_WINDOW = true

    fun appWidthDp(landscape: Boolean): Int =
        if (landscape) LANDSCAPE_WIDTH_DP else PORTRAIT_WIDTH_DP
}
