package com.sheout.content.internal;

import com.sheout.content.ContentApi;
import com.sheout.content.ContentBlock;
import com.sheout.content.ContentError;
import com.sheout.sharedkernel.Result;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ContentService implements ContentApi {

    static final int MAX_VALUE_LENGTH = 2000;

    private final ContentBlockRepository blocks;

    ContentService(ContentBlockRepository blocks) {
        this.blocks = blocks;
    }

    @Override
    public Optional<ContentBlock> getContent(String key) {
        return blocks.findByContentKey(key).map(ContentService::toBlock);
    }

    @Override
    public List<ContentBlock> getContentByPrefix(String prefix) {
        return blocks.findByContentKeyStartingWithOrderByContentKeyAsc(prefix == null ? "" : prefix).stream()
                .map(ContentService::toBlock)
                .toList();
    }

    @Override
    @Transactional
    public Result<ContentBlock, ContentError> updateContent(String key, String value, long expectedVersion, UUID updatedBy) {
        // Blank is refused rather than stored: an empty banner headline is
        // never intended, and a field that silently empties reads as broken.
        if (value == null || value.isBlank() || value.length() > MAX_VALUE_LENGTH) {
            return Result.failure(ContentError.INVALID_VALUE);
        }
        Optional<ContentBlockEntity> found = blocks.findByContentKey(key);
        if (found.isEmpty()) {
            return Result.failure(ContentError.NOT_FOUND);
        }
        ContentBlockEntity block = found.get();
        if (block.getVersion() != expectedVersion) {
            return Result.failure(ContentError.STALE_VERSION);
        }
        block.edit(value.strip(), updatedBy);
        try {
            return Result.success(toBlock(blocks.saveAndFlush(block)));
        } catch (ObjectOptimisticLockingFailureException concurrentSave) {
            // Another save landed between our read and our write.
            return Result.failure(ContentError.STALE_VERSION);
        }
    }

    private static ContentBlock toBlock(ContentBlockEntity e) {
        return new ContentBlock(e.getKey(), e.getValue(), e.getDescription(), e.getVersion(), e.getUpdatedAt(), e.getUpdatedBy());
    }
}
