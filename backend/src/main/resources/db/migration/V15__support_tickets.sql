-- Support tickets: a rider or a partner raising an issue, and the thread of
-- replies that follows.
--
-- Two tables in the support module's own name. Nothing here is a copy of
-- another module's data: linked_booking_id and every account id are plain
-- references, resolved through the owning module's public interface when
-- something needs a name or a phone number.

CREATE TABLE support_tickets (
    id                   UUID          PRIMARY KEY,
    raised_by_account_id UUID          NOT NULL,
    -- CUSTOMER or DRIVER, taken from the raiser's token when the ticket is
    -- created. Stored so the queue can be filtered and read without asking
    -- auth about every row.
    raised_by_role       VARCHAR(20)   NOT NULL,
    category             VARCHAR(40)   NOT NULL,
    subject              VARCHAR(150)  NOT NULL,
    description          VARCHAR(2000) NOT NULL,
    linked_booking_id    UUID,
    status               VARCHAR(20)   NOT NULL,
    priority             VARCHAR(10)   NOT NULL,
    -- Unresolved before resolved, then HIGH before LOW - see
    -- SupportTicketEntity.queueRank. A column rather than a CASE in the
    -- query, so the queue sorts with a plain index and Spring Data paging.
    queue_rank           INTEGER       NOT NULL,
    assigned_admin_id    UUID,
    -- Same audit convention as sos_alert: who closed it and when, set
    -- together, null until then.
    resolved_at          TIMESTAMPTZ,
    resolved_by          UUID,
    -- The "last update" a list shows. Moved by a new message or a status
    -- change, not by an unrelated write, which is why it is not updated_at.
    last_activity_at     TIMESTAMPTZ   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    updated_at           TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_support_tickets_raised_by ON support_tickets (raised_by_account_id, last_activity_at DESC);
CREATE INDEX idx_support_tickets_queue ON support_tickets (queue_rank, last_activity_at DESC);
CREATE INDEX idx_support_tickets_booking ON support_tickets (linked_booking_id);

CREATE TABLE support_ticket_messages (
    id                 UUID          PRIMARY KEY,
    ticket_id          UUID          NOT NULL REFERENCES support_tickets (id),
    author_account_id  UUID          NOT NULL,
    -- CUSTOMER, DRIVER or ADMIN. Lets a thread say "SheOut Support" for a
    -- reply without telling the rider which operator wrote it.
    author_role        VARCHAR(20)   NOT NULL,
    message            VARCHAR(2000) NOT NULL,
    -- True for an operator's note to other operators. Never returned to the
    -- person who raised the ticket, and never triggers a notification.
    internal_only      BOOLEAN       NOT NULL,
    created_at         TIMESTAMPTZ   NOT NULL,
    updated_at         TIMESTAMPTZ   NOT NULL
);

CREATE INDEX idx_support_ticket_messages_ticket ON support_ticket_messages (ticket_id, created_at);
