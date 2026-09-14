package com.sheout.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the two rules every other part of tickets leans on: which status
 * moves are allowed, and which categories are urgent.
 */
class SupportTicketRulesTest {

    @Test
    @DisplayName("a safety concern is always HIGH; nothing else is")
    void safetyIsHigh() {
        for (SupportTicketCategory category : SupportTicketCategory.values()) {
            assertEquals(
                    category == SupportTicketCategory.SAFETY_CONCERN ? SupportTicketPriority.HIGH : SupportTicketPriority.MEDIUM,
                    SupportTicketPriority.forCategory(category),
                    category.name());
        }
    }

    @Test
    @DisplayName("CLOSED is final")
    void closedIsFinal() {
        for (SupportTicketStatus next : SupportTicketStatus.values()) {
            assertFalse(SupportTicketStatus.CLOSED.canMoveTo(next), "CLOSED -> " + next);
        }
    }

    @Test
    @DisplayName("every open state can be resolved or closed, and a resolved ticket can be reopened")
    void ordinaryMoves() {
        assertTrue(SupportTicketStatus.OPEN.canMoveTo(SupportTicketStatus.IN_PROGRESS));
        assertTrue(SupportTicketStatus.IN_PROGRESS.canMoveTo(SupportTicketStatus.RESOLVED));
        assertTrue(SupportTicketStatus.RESOLVED.canMoveTo(SupportTicketStatus.OPEN));
        assertTrue(SupportTicketStatus.RESOLVED.canMoveTo(SupportTicketStatus.CLOSED));
        assertFalse(SupportTicketStatus.OPEN.canMoveTo(SupportTicketStatus.OPEN));
    }
}
