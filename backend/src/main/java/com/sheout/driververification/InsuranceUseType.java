package com.sheout.driververification;

/**
 * What a vehicle insurance policy says the vehicle is used for.
 * <p>
 * Matters because a private-use policy can be refused when the vehicle
 * carries a paying passenger - the one moment it is needed. An operator
 * cannot approve a PRIVATE or UNKNOWN policy for a partner who carries
 * passengers; see DocumentRequirements.
 */
public enum InsuranceUseType {
    COMMERCIAL,
    PRIVATE,
    UNKNOWN
}
