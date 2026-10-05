package com.sheout.driververification;

/**
 * Whose name is on the RC of the vehicle she rides.
 * <p>
 * Many women ride a scooter registered to a parent or husband. That is not
 * a reason to turn her away, and an RC in another name is not a mismatch to
 * reject - so she says whose it is, with the owner's name as printed, and
 * the reviewer checks the RC against that instead of against her own name.
 * <p>
 * Stored by name; never rename a value while rows carry it.
 */
public enum VehicleOwner {
    SELF,
    FATHER,
    MOTHER,
    HUSBAND,
    SIBLING,
    OTHER_FAMILY;

    /** Said in a reviewer's words. */
    public String plainName() {
        return switch (this) {
            case SELF -> "her own";
            case FATHER -> "her father's";
            case MOTHER -> "her mother's";
            case HUSBAND -> "her husband's";
            case SIBLING -> "her brother's or sister's";
            case OTHER_FAMILY -> "a family member's";
        };
    }
}
