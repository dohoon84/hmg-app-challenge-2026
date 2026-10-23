package io.hmg.appchallenge.foundation.domain

/**
 * CarUxRestrictions bits the UI can read. Values match android.car.
 * When [failClosed] and the UX service has not reported, every restriction is on
 * and string length collapses to empty. That is the most restrictive state.
 */
object UxRestrictionBits {
    const val BASELINE = 0
    const val NO_DIALPAD = 1
    const val NO_FILTERING = 2
    const val LIMIT_STRING_LENGTH = 4
    const val NO_KEYBOARD = 8
    const val NO_VIDEO = 16
    const val LIMIT_CONTENT = 32
    const val NO_SETUP = 64
    const val NO_TEXT_MESSAGE = 128
    const val NO_VOICE_TRANSCRIPTION = 256
    const val FULLY_RESTRICTED = 511
}

data class UxRestrictionState(
    val uxKnown: Boolean,
    val noDialpad: Boolean,
    val noFiltering: Boolean,
    val limitStringLength: Boolean,
    val noKeyboard: Boolean,
    val noVideo: Boolean,
    val limitContent: Boolean,
    val noSetup: Boolean,
    val noTextMessage: Boolean,
    val noVoiceTranscription: Boolean,
    val maxRestrictedStringLength: Int,
) {
    val keyboardAllowed: Boolean get() = !noKeyboard

    fun clip(text: String): String {
        if (!limitStringLength) return text
        if (maxRestrictedStringLength <= 0) return ""
        return text.take(maxRestrictedStringLength)
    }

    companion object {
        fun from(
            uxAvailable: Boolean,
            mask: Int,
            maxRestrictedStringLength: Int,
            failClosed: Boolean,
        ): UxRestrictionState {
            val locked = failClosed && !uxAvailable
            val active = if (locked) UxRestrictionBits.FULLY_RESTRICTED else mask
            return UxRestrictionState(
                uxKnown = uxAvailable,
                noDialpad = active.has(UxRestrictionBits.NO_DIALPAD),
                noFiltering = active.has(UxRestrictionBits.NO_FILTERING),
                limitStringLength = active.has(UxRestrictionBits.LIMIT_STRING_LENGTH),
                noKeyboard = active.has(UxRestrictionBits.NO_KEYBOARD),
                noVideo = active.has(UxRestrictionBits.NO_VIDEO),
                limitContent = active.has(UxRestrictionBits.LIMIT_CONTENT),
                noSetup = active.has(UxRestrictionBits.NO_SETUP),
                noTextMessage = active.has(UxRestrictionBits.NO_TEXT_MESSAGE),
                noVoiceTranscription = active.has(UxRestrictionBits.NO_VOICE_TRANSCRIPTION),
                maxRestrictedStringLength = if (locked) 0 else maxRestrictedStringLength,
            )
        }
    }
}

private fun Int.has(bit: Int): Boolean = this and bit != 0

object KeyboardPolicy {
    fun allowsKeyboard(mask: Int, uxAvailable: Boolean, failClosed: Boolean): Boolean =
        UxRestrictionState.from(
            uxAvailable = uxAvailable,
            mask = mask,
            maxRestrictedStringLength = 0,
            failClosed = failClosed,
        ).keyboardAllowed
}
