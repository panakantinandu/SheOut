package com.sheout.driververification.internal.checks;

import com.sheout.driververification.CheckStatus;
import com.sheout.driververification.CheckType;
import org.springframework.stereotype.Component;

/**
 * The default for every check: a person looks. The answer puts the check in
 * front of a reviewer (NEEDS_REVIEW), with what to look at, and that row is
 * the review item - it is what the console's queues list.
 */
@Component
public class ManualCheckProvider implements CheckProvider {

    public static final String NAME = "manual";

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean handles(CheckType type) {
        return true;
    }

    @Override
    public Verdict check(Request request) {
        if (request.type() == CheckType.PAN) {
            // Optional and never part of approval. Its format was checked when
            // she typed it; asking a person to look at it would only teach
            // reviewers to wave "needs review" through.
            return new Verdict(CheckStatus.PASSED, "Format checked when she entered it. Used for payouts only.", null);
        }
        return new Verdict(CheckStatus.NEEDS_REVIEW, switch (request.type()) {
            case IDENTITY -> "A person reads her ID: a government photo ID, her name, female, 18 or older.";
            case SELFIE_MATCH -> "A person compares her live selfie with the photo on her ID.";
            case LICENCE -> "A person reads her licence and checks it on Parivahan (Sarathi).";
            case VEHICLE -> "A person reads the RC and checks it on Parivahan (Vahan).";
            case INSURANCE -> "A person reads the policy: this vehicle, commercial use, in date.";
            case PAN -> "A person checks her PAN.";
            case BACKGROUND -> "A person reads her police certificate.";
            case COURT_RECORD_SCREEN -> "A person searches public court records by hand. A screening aid, not police verification.";
        }, null);
    }
}
