package com.sheout.support;

import java.util.List;

/** A ticket and its thread, oldest message first. */
public record SupportTicketDetail(SupportTicketSummary ticket, List<SupportTicketMessage> messages) {
}
