package com.sheout.content;

import java.time.Instant;
import java.util.UUID;

/**
 * One piece of editable app copy.
 * <p>
 * version is what an edit must send back (see ContentApi.updateContent);
 * updatedBy is null while the block still holds its seeded text.
 */
public record ContentBlock(
        String key,
        String value,
        String description,
        long version,
        Instant updatedAt,
        UUID updatedBy
) {
}
