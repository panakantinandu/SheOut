package com.sheout.driververification.internal;

import com.sheout.driververification.PartnerDocumentType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface FlaggedDocumentNumberRepository extends JpaRepository<FlaggedDocumentNumberEntity, UUID> {

    List<FlaggedDocumentNumberEntity> findByDocumentTypeAndDocumentNumber(PartnerDocumentType documentType, String documentNumber);

    boolean existsByDocumentId(UUID documentId);
}
