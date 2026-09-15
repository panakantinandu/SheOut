package com.sheout.sharedkernel.storage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface StoredDocumentRepository extends JpaRepository<StoredDocumentEntity, UUID> {

    Optional<StoredDocumentEntity> findByStorageKey(String storageKey);

    @Modifying
    @Query("delete from StoredDocumentEntity d where d.storageKey = :storageKey")
    int deleteByStorageKey(@Param("storageKey") String storageKey);
}
