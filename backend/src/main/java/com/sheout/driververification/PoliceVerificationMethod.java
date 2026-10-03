package com.sheout.driververification;

/**
 * How a partner's police verification was carried out.
 * <p>
 * TS_POLICE_PVC is a Police Verification Certificate from Telangana Police's
 * i-Verify portal (https://pvc.tspolice.gov.in/) - the route SheOut expects
 * for a partner who lives in Telangana. OTHER_STATE_POLICE is the same from
 * another state's police. THIRD_PARTY_BGV is a private background check: it
 * may be attached beside a police certificate, but stands alone only when
 * POLICE_ACCEPT_THIRD_PARTY_BGV_ALONE is true, because whether it satisfies
 * the aggregator guidelines' "police verification" in Telangana is a legal
 * question nobody has answered yet (see the README).
 */
public enum PoliceVerificationMethod {
    TS_POLICE_PVC,
    OTHER_STATE_POLICE,
    THIRD_PARTY_BGV
}
