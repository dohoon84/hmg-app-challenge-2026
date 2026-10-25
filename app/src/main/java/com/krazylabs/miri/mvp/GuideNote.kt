package com.krazylabs.miri.mvp

/**
 * The F3 guide note. It leaves after [HOLD_MS] and when the card changes,
 * so a tap on one return card does not sit on the next one.
 */
object GuideNote {
    const val HOLD_MS = 3_000L

    fun retain(
        note: String,
        setAt: Long,
        now: Long,
        latchedCard: String,
        card: String,
    ): String {
        if (note.isBlank()) return ""
        if (latchedCard.isNotEmpty() && card != latchedCard) return ""
        if (setAt > 0L && now - setAt >= HOLD_MS) return ""
        return note
    }
}
