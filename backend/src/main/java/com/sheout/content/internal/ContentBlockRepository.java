package com.sheout.content.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ContentBlockRepository extends JpaRepository<ContentBlockEntity, UUID> {

    Optional<ContentBlockEntity> findByContentKey(String contentKey);

    List<ContentBlockEntity> findByContentKeyStartingWithOrderByContentKeyAsc(String prefix);
}
