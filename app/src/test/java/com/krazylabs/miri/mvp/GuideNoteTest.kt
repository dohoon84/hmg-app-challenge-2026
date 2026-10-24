package com.krazylabs.miri.mvp

import org.junit.Assert.assertEquals
import org.junit.Test

class GuideNoteTest {
    private val note = ReturnGuidanceFeature.NOTE

    @Test
    fun theNoteStaysForThreeSecondsOnTheSameCard() {
        assertEquals(note, GuideNote.retain(note, 1_000L, 1_000L, "", "F3#1"))
        assertEquals(note, GuideNote.retain(note, 1_000L, 3_999L, "F3#1", "F3#1"))
        assertEquals("", GuideNote.retain(note, 1_000L, 4_000L, "F3#1", "F3#1"))
    }

    @Test
    fun theNoteClearsWhenTheCardChanges() {
        assertEquals("", GuideNote.retain(note, 1_000L, 1_500L, "F3#1", "F3#2"))
        assertEquals("", GuideNote.retain(note, 1_000L, 1_500L, "F3#1", "CHECK:반납 전 점검"))
        assertEquals("", GuideNote.retain("", 1_000L, 1_100L, "F3#1", "F3#1"))
    }
}
