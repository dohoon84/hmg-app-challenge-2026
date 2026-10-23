package io.hmg.appchallenge.foundation.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import io.hmg.appchallenge.foundation.domain.AppShell

/** Official basic-button heights. Small is parked-only. */
enum class IviButtonSize {
    LARGE,
    MEDIUM,
    SMALL,
}

object IviButtonMetrics {
    const val LARGE_DP = 100
    const val MEDIUM_DP = 80
    const val SMALL_DP = 64
    const val LARGE_RADIUS_DP = 32
    const val MEDIUM_RADIUS_DP = 24
    const val SMALL_RADIUS_DP = 16

    fun heightDp(size: IviButtonSize): Int = when (size) {
        IviButtonSize.LARGE -> LARGE_DP
        IviButtonSize.MEDIUM -> MEDIUM_DP
        IviButtonSize.SMALL -> SMALL_DP
    }

    fun radiusDp(size: IviButtonSize): Int = when (size) {
        IviButtonSize.LARGE -> LARGE_RADIUS_DP
        IviButtonSize.MEDIUM -> MEDIUM_RADIUS_DP
        IviButtonSize.SMALL -> SMALL_RADIUS_DP
    }
}

/** A reduced shell promotes Small to Medium. Large and Medium stay as requested. */
fun resolveButtonSize(requested: IviButtonSize, shell: AppShell): IviButtonSize =
    if (shell == AppShell.REDUCED && requested == IviButtonSize.SMALL) {
        IviButtonSize.MEDIUM
    } else {
        requested
    }

object PressFeedback {
    const val PRESSED_SCALE = 0.95f
    const val DURATION_MS = 80
}

fun pressScaleOf(pressed: Boolean): Float =
    if (pressed) PressFeedback.PRESSED_SCALE else 1f

@Composable
fun Modifier.pressScale(interactionSource: InteractionSource): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = pressScaleOf(pressed),
        animationSpec = tween(durationMillis = PressFeedback.DURATION_MS),
        label = "ivi_press_scale",
    )
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
