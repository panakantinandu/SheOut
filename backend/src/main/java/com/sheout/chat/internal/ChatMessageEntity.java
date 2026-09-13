package com.sheout.chat.internal;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "chat_messages")
public class ChatMessageEntity extends BaseEntity {

    @Column(nullable = false)
    private UUID bookingId;

    @Column(nullable = false)
    private UUID senderAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountRole senderRole;

    @Column(nullable = false, length = 1000)
    private String body;

    protected ChatMessageEntity() {
        // JPA
    }

    public ChatMessageEntity(UUID bookingId, UUID senderAccountId, AccountRole senderRole, String body) {
        this.bookingId = bookingId;
        this.senderAccountId = senderAccountId;
        this.senderRole = senderRole;
        this.body = body;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public UUID getSenderAccountId() {
        return senderAccountId;
    }

    public AccountRole getSenderRole() {
        return senderRole;
    }

    public String getBody() {
        return body;
    }

    // No setter for body, deliberately. A message that can be edited after
    // the fact is worthless as a record of what was said, which is the only
    // reason a closed thread is kept at all.
}
