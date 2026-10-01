package com.sheout.users.internal;

import com.sheout.sharedkernel.BaseEntity;
import com.sheout.users.VehicleType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** See V50 and DriverProfileService.updateProfile. */
@Entity
@Table(name = "driver_profile_changes")
class DriverProfileChangeEntity extends BaseEntity {

    enum Status { PENDING, APPROVED, REJECTED, WITHDRAWN }

    @Column(name = "account_id", nullable = false)
    private UUID accountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(length = 150)
    private String name;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", length = 20)
    private VehicleType vehicleType;

    @Column(name = "vehicle_registration_number", length = 20)
    private String vehicleRegistrationNumber;

    @Column(name = "photo_key", length = 512)
    private String photoKey;

    @Column(name = "rc_document_key", length = 512)
    private String rcDocumentKey;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decision_note", length = 500)
    private String decisionNote;

    protected DriverProfileChangeEntity() {
    }

    DriverProfileChangeEntity(UUID accountId) {
        this.accountId = accountId;
        this.status = Status.PENDING;
        this.requestedAt = Instant.now();
    }

    /** The identity fields she has asked to change, each null where it matches what was approved. */
    void requestDetails(String name, LocalDate dateOfBirth, VehicleType vehicleType, String registration) {
        this.name = name;
        this.dateOfBirth = dateOfBirth;
        this.vehicleType = vehicleType;
        this.vehicleRegistrationNumber = registration;
        // The certificate belongs to the vehicle asked for; a different one needs a new photo.
        if (!vehicleChanged()) {
            this.rcDocumentKey = null;
        }
        this.requestedAt = Instant.now();
    }

    void requestPhoto(String key) {
        this.photoKey = key;
        this.requestedAt = Instant.now();
    }

    void attachRcDocument(String key) {
        this.rcDocumentKey = key;
    }

    boolean vehicleChanged() {
        return vehicleType != null || vehicleRegistrationNumber != null;
    }

    boolean isEmpty() {
        return name == null && dateOfBirth == null && !vehicleChanged() && photoKey == null;
    }

    void decide(Status outcome, UUID by, String note) {
        this.status = outcome;
        this.decidedAt = Instant.now();
        this.decidedBy = by;
        this.decisionNote = note;
    }

    UUID getAccountId() {
        return accountId;
    }

    Status getStatus() {
        return status;
    }

    String getName() {
        return name;
    }

    LocalDate getDateOfBirth() {
        return dateOfBirth;
    }

    VehicleType getVehicleType() {
        return vehicleType;
    }

    String getVehicleRegistrationNumber() {
        return vehicleRegistrationNumber;
    }

    String getPhotoKey() {
        return photoKey;
    }

    String getRcDocumentKey() {
        return rcDocumentKey;
    }

    Instant getRequestedAt() {
        return requestedAt;
    }

    Instant getDecidedAt() {
        return decidedAt;
    }

    String getDecisionNote() {
        return decisionNote;
    }
}
