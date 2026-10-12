package com.brian.dialbuddy.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class VisibleMailboxTabsTest {
    @Test
    fun emptyMailboxShowsOnlyStats() {
        assertEquals(listOf(MailboxTab.Stats), visibleMailboxTabs(0, 0))
    }

    @Test
    fun recognizedMessagesKeepThatCard() {
        assertEquals(
            listOf(MailboxTab.Known, MailboxTab.Stats),
            visibleMailboxTabs(2, 0),
        )
    }

    @Test
    fun unknownMessagesKeepThatCard() {
        assertEquals(
            listOf(MailboxTab.Unknown, MailboxTab.Stats),
            visibleMailboxTabs(0, 4),
        )
    }

    @Test
    fun bothListsStayInOrderAheadOfStats() {
        assertEquals(
            listOf(MailboxTab.Known, MailboxTab.Unknown, MailboxTab.Stats),
            visibleMailboxTabs(1, 1),
        )
    }
}
