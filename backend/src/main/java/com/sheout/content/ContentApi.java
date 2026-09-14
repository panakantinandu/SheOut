package com.sheout.content;

import com.sheout.sharedkernel.Result;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * App copy that operators can change without a release: Home's banner and
 * community card, Help & Support's intro and FAQs, About.
 * <p>
 * Reads are for anyone - this is the text the apps show, not anybody's data.
 * Writes change a value only, never a key, and are for operators: the role
 * gate lives in the admin module's controller, the same place every other
 * operator action is gated.
 */
public interface ContentApi {

    Optional<ContentBlock> getContent(String key);

    /** Every block whose key starts with prefix, ordered by key - a whole section in one read. */
    List<ContentBlock> getContentByPrefix(String prefix);

    /**
     * Replaces a block's value. expectedVersion must be the version the editor
     * loaded; if the block has been saved since, nothing changes and the
     * result is STALE_VERSION, so one operator never silently overwrites
     * another. updatedBy is recorded for the audit trail.
     */
    Result<ContentBlock, ContentError> updateContent(String key, String value, long expectedVersion, UUID updatedBy);
}
