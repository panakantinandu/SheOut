package com.sheout.driververification;

import com.sheout.auth.AccountRole;
import com.sheout.sharedkernel.event.DomainEvent;

import java.util.UUID;

/**
 * She moved forward a stage, and is told so in plain words, by push and
 * email: we have what she sent, or a person approved it and here is what
 * comes next. Being ready to work is AccountVerified, and a step sent back
 * is VerificationRejected or PartnerDocumentRejected, so between them she
 * hears at every stage.
 * <p>
 * Raised by the checks engine when a check changes stage, and never on the
 * first look at an account that existed before checks did, so nobody is
 * told years later that her identity "is approved".
 */
public class VerificationStageReached extends DomainEvent {

    public enum Stage {
        /** Her ID and selfie arrived. */
        ID_RECEIVED,
        /** A person approved her identity. Partners only: a rider is then simply verified. */
        IDENTITY_APPROVED,
        /** Her licence, RC and policy are all sent. */
        VEHICLE_PAPERS_RECEIVED,
        /** A person approved all of them. */
        VEHICLE_APPROVED,
        /** Her police certificate arrived. */
        SAFETY_CHECK_RECEIVED
    }

    private final UUID accountId;
    private final AccountRole role;
    private final Stage stage;

    public VerificationStageReached(UUID accountId, AccountRole role, Stage stage) {
        this.accountId = accountId;
        this.role = role;
        this.stage = stage;
    }

    public UUID accountId() {
        return accountId;
    }

    public AccountRole role() {
        return role;
    }

    public Stage stage() {
        return stage;
    }
}
