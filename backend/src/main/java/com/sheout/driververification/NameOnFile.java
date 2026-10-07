package com.sheout.driververification;

/**
 * One place her name is written, and how it compares with her profile -
 * for the reviewer's "her name across every document" panel.
 * <p>
 * {@code match}: PROFILE (this is the profile itself); SAME; VARIANT (the
 * same name written another way - Laxmi and Lakshmi, initials, either
 * order); DIFFERENT (worth a look; never a verdict); DECLARED (she said it
 * matches her profile instead of typing it, so there is nothing to compare);
 * FAMILY (a family member's vehicle: a different name is expected);
 * READ_FROM_PHOTO (nothing typed - the reviewer reads it off the image).
 */
public record NameOnFile(String source, String name, String match) {
}
