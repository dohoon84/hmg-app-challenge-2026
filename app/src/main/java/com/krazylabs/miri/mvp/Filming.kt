package com.krazylabs.miri.mvp

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Debug filming for a CF-style demo. Release binds [IdleFilming], whose
 * [active] flag stays false. The broadcast receiver that turns this on is
 * not in the release manifest.
 */
interface Filming {
    val active: StateFlow<Boolean>

    /** Bumps when a play or stop command is waiting in [drain]. */
    val signals: StateFlow<Int>

    fun drain(): List<FilmingCommand>
}

@Singleton
class IdleFilming @Inject constructor() : Filming {
    override val active: StateFlow<Boolean> = MutableStateFlow(false)
    override val signals: StateFlow<Int> = MutableStateFlow(0)
    override fun drain(): List<FilmingCommand> = emptyList()
}

/**
 * Which debug chrome is on screen. Product screens do not read this for color;
 * [useDark] only selects the existing dark theme.
 */
data class FilmingChrome(
    val scenarioPanel: Boolean = false,
    val longPress: Boolean = false,
    val applyValues: Boolean = false,
    val demoValueIndicators: Boolean = false,
    val captions: Boolean = false,
    val highlights: Boolean = false,
    val returnGuide: Boolean = false,
    val foundationOverlay: Boolean = false,
    val lampRow: Boolean = false,
    val toasts: Boolean = false,
) {
    /** There is no toast in the app. A debug message stays hidden while this is false. */
    fun toastText(message: String): String? = if (toasts && message.isNotBlank()) message else null

    companion object {
        fun of(filming: Boolean, debug: Boolean, returnGuidance: Boolean): FilmingChrome {
            val dev = debug && !filming
            return FilmingChrome(
                scenarioPanel = dev,
                longPress = dev,
                applyValues = dev,
                demoValueIndicators = dev,
                captions = dev,
                highlights = dev,
                returnGuide = dev && returnGuidance,
                foundationOverlay = dev,
                lampRow = dev,
                toasts = dev,
            )
        }

        /** Filming forces the existing dark theme. Otherwise the system night mode wins. */
        fun useDark(systemDark: Boolean, filming: Boolean): Boolean = filming || systemDark

        /** Release packages the build flag as false, so the mode is not in that APK. */
        fun ships(releaseFlag: Boolean): Boolean = releaseFlag
    }
}

data class FilmingExtras(
    val on: Boolean? = null,
    val start: Boolean? = null,
    val step: String? = null,
    val hold: Float? = null,
    val profile: String? = null,
    val seconds: Int? = null,
)

sealed interface FilmingCommand {
    data class Set(val on: Boolean) : FilmingCommand

    /**
     * [profile] null is the rehearsal script.
     * `storyboard-v1` uses the cut-table holds.
     * [seconds] replaces that cut's hold. Null keeps the profile.
     */
    data class Play(
        val step: String?,
        val holdScale: Float,
        val profile: String? = null,
        val seconds: Int? = null,
    ) : FilmingCommand

    data object Stop : FilmingCommand
    data object Ignore : FilmingCommand
}

/**
 * Broadcast contract for the debug receiver.
 * [steps] are the rehearsal beats. C3 is 다녀왔어요, before 점검 끝.
 * [storyboardSteps] are the CF cut table. Editing cards are not steps.
 */
object FilmingCommands {
    const val ACTION_FILMING = "com.krazylabs.miri.action.FILMING"
    const val ACTION_AUTOPLAY = "com.krazylabs.miri.action.AUTOPLAY"
    const val EXTRA_ON = "on"
    const val EXTRA_START = "start"
    const val EXTRA_STEP = "step"
    const val EXTRA_HOLD = "hold"
    const val EXTRA_PROFILE = "profile"
    const val EXTRA_SECONDS = "seconds"
    const val PROFILE_STORYBOARD_V1 = "storyboard-v1"

    /** Beat ids the rehearsal script uses, in order. */
    val steps: List<String> = listOf("F1", "F2", "D1", "D2", "F3", "C1", "C3", "C2")

    /** App cuts in the storyboard. Opening, hook, charge close-up, and ending are absent. */
    val storyboardSteps: List<String> = listOf(
        "F1", "F2", "D1", "DRIVE1", "D2", "DRIVE2", "F3", "C1", "C2",
    )

    fun resolveStep(raw: String, profile: String? = null): String? {
        val id = raw.trim()
        val allowed = if (profile == PROFILE_STORYBOARD_V1) storyboardSteps else steps
        return id.takeIf { it in allowed }
    }

    fun parse(action: String?, extras: FilmingExtras): FilmingCommand {
        return when (action) {
            ACTION_FILMING -> {
                val on = extras.on ?: return FilmingCommand.Ignore
                FilmingCommand.Set(on)
            }
            ACTION_AUTOPLAY -> {
                val start = extras.start ?: return FilmingCommand.Ignore
                if (!start) return FilmingCommand.Stop
                val profile = extras.profile?.trim()?.takeIf { it.isNotEmpty() }
                if (profile != null && profile != PROFILE_STORYBOARD_V1) {
                    return FilmingCommand.Ignore
                }
                val raw = extras.step?.trim().orEmpty()
                val step = if (raw.isEmpty()) {
                    null
                } else {
                    resolveStep(raw, profile) ?: return FilmingCommand.Ignore
                }
                FilmingCommand.Play(step, holdScale(extras.hold), profile, holdSeconds(extras.seconds))
            }
            else -> FilmingCommand.Ignore
        }
    }

    fun holdScale(hold: Float?): Float =
        if (hold != null && hold.isFinite() && hold > 0f) hold else 1f

    /** Whole seconds for the cut that is starting. Zero and negative keep the profile. */
    fun holdSeconds(seconds: Int?): Int? = seconds?.takeIf { it > 0 }
}
