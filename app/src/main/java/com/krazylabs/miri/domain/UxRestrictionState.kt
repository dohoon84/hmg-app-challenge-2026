package com.krazylabs.miri.domain

/**
 * CarUxRestrictions bits the UI can read. Values match android.car.
 * When [failClosed] and the UX service has not reported, every restriction is on.
 * String length then uses [UxRestrictionState.UNKNOWN_MAX_STRING_LENGTH] (the AAOS
 * `car_ux_restrictions` baseline of 120). A limit of 0 or less never blanks text,
 * and [UxRestrictionState.clip] with `preserve` never shortens a safety warning.
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

    /**
     * Shortens [text] to the active limit.
     * [preserve] is for safety copy such as the door-open warning: the text is never
     * shortened or removed, even when the limit is one character.
     * A limit of 0 or less keeps [text]. Unknown UX uses [UNKNOWN_MAX_STRING_LENGTH].
     */
    fun clip(text: String, preserve: Boolean = false): String {
        if (preserve || !limitStringLength) return text
        val limit = maxRestrictedStringLength
        if (limit <= 0) return text
        return text.take(limit)
    }

    companion object {
        /**
         * AAOS `frameworks/base/core/res/res/values/car_ux_restrictions.xml` default for
         * `ux_restrictions_limit_string_length` when the car has not reported a length.
         * Applied only while UX is unknown and the flavor fails closed.
         */
        const val UNKNOWN_MAX_STRING_LENGTH = 120

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
                maxRestrictedStringLength = if (locked) {
                    UNKNOWN_MAX_STRING_LENGTH
                } else {
                    maxRestrictedStringLength
                },
            )
        }
    }
}

private fun Int.has(bit: Int): Boolean = this and bit != 0
