package com.brian.dialbuddy.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoicemailRulesTest {
    private fun entry(
        id: Long,
        seconds: Int?,
        archived: Boolean = false,
        deleted: Boolean = false,
        number: String? = "555",
    ) = VoicemailEntry(id, number, seconds, 0L, archived, deleted)

    @Test
    fun emptyMeansShorterThanThresholdAndStillInTheMailbox() {
        assertTrue(entry(1, 2).isEmpty(3))
        assertFalse(entry(1, 3).isEmpty(3))
        assertFalse(entry(1, 2, archived = true).isEmpty(3))
        assertFalse(entry(1, 2, deleted = true).isEmpty(3))
        assertFalse(entry(1, null).isEmpty(3))
        assertFalse(entry(1, -1).isEmpty(3))
    }

    @Test
    fun thresholdClampsToOneThroughTwenty() {
        assertEquals(1, clampThreshold(0))
        assertEquals(20, clampThreshold(99))
        assertEquals(3, clampThreshold(3))
    }

    @Test
    fun bulkDeleteSkipsRecognizedNumbers() {
        val entries = listOf(
            entry(1, 1, number = "5550100"),
            entry(2, 1, number = "5550199"),
            entry(3, 30, number = "5550199"),
        )
        val bulk = bulkVoicemails(entries, 3, setOf("5550100"))
        assertEquals(listOf(2L), bulk.map { it.id })
    }

    @Test
    fun deletionStampsRoundTripAndStayInsideTheWindow() {
        val stamps = listOf(DeletionStamp(1_000L, 2), DeletionStamp(5_000L, 4))
        assertEquals(stamps, parseDeletionStamps(formatDeletionStamps(stamps)))
        assertEquals(emptyList<DeletionStamp>(), parseDeletionStamps("bad;3:0;x:2"))
        val now = 10_000L
        assertEquals(4L, deletedInWindow(stamps, now, 6_000L))
        assertEquals(12L, nextDeletedTotal(10, 2))
    }

    @Test
    fun negativeTotalsAreRejected() {
        try {
            nextDeletedTotal(-1, 1)
            throw AssertionError("expected total rejection")
        } catch (error: IllegalArgumentException) {
            assertEquals("total cannot be negative", error.message)
        }
    }
}
