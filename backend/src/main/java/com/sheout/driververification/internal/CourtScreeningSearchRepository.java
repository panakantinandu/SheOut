package com.sheout.driververification.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface CourtScreeningSearchRepository extends JpaRepository<CourtScreeningSearchEntity, UUID> {

    List<CourtScreeningSearchEntity> findByScreeningIdOrderByCreatedAtAsc(UUID screeningId);
}
