package com.sheout.users.internal;

import com.sheout.auth.AuthApi;
import com.sheout.driververification.VerificationApi;
import com.sheout.driververification.VerificationStatus;
import com.sheout.driververification.VerificationSummary;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import com.sheout.sharedkernel.storage.DocumentStorage;
import com.sheout.sharedkernel.storage.DocumentUpload;
import com.sheout.auth.AccountRole;
import com.sheout.users.DriverProfileChangeDecided;
import com.sheout.users.VehicleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DriverProfileChangeServiceTest {

    private final DriverProfileChangeRepository changes = mock(DriverProfileChangeRepository.class);
    private final DriverProfileRepository profiles = mock(DriverProfileRepository.class);
    private final VerificationApi verification = mock(VerificationApi.class);
    private final DocumentStorage storage = mock(DocumentStorage.class);
    private final DomainEventPublisher events = mock(DomainEventPublisher.class);
    private final UUID partner = UUID.randomUUID();
    private DriverProfileChangeService service;
    private DriverProfileEntity profile;

    @BeforeEach
    void setUp() {
        service = new DriverProfileChangeService(changes, profiles, verification, storage, mock(AuthApi.class), events);
        profile = new DriverProfileEntity(partner);
        profile.setName("Lakshmi");
        profile.setDateOfBirth(LocalDate.of(1995, 4, 2));
        profile.setVehicleType(VehicleType.BIKE);
        profile.setVehicleRegistrationNumber("TS09EA4521");
        profile.setProfilePhotoKey("photo-approved");
        when(profiles.findByAccountId(partner)).thenReturn(Optional.of(profile));
        when(changes.findFirstByAccountIdAndStatus(partner, DriverProfileChangeEntity.Status.PENDING)).thenReturn(Optional.empty());
    }

    private DriverProfileChangeEntity savedChange() {
        ArgumentCaptor<DriverProfileChangeEntity> c = ArgumentCaptor.forClass(DriverProfileChangeEntity.class);
        verify(changes).save(c.capture());
        return c.getValue();
    }

    @Test
    void identityIsLockedOnlyOnceHerIdIsApproved() {
        when(verification.findByAccountId(partner)).thenReturn(Optional.of(new VerificationSummary(partner, AccountRole.DRIVER,
                VerificationStatus.UNDER_REVIEW, VerificationStatus.PENDING, true, null, Instant.now(), Instant.now())));
        assertThat(service.identityLocked(partner)).isFalse();
        when(verification.findByAccountId(partner)).thenReturn(Optional.of(new VerificationSummary(partner, AccountRole.DRIVER,
                VerificationStatus.VERIFIED, VerificationStatus.VERIFIED, true, null, Instant.now(), Instant.now())));
        assertThat(service.identityLocked(partner)).isTrue();
    }

    @Test
    void onlyWhatChangedBecomesPendingAndTheProfileIsUntouched() {
        service.requestDetails(profile, "Lakshmi", LocalDate.of(1995, 4, 2), VehicleType.BIKE, "TS09EB1111");

        DriverProfileChangeEntity change = savedChange();
        assertThat(change.getName()).isNull();
        assertThat(change.getDateOfBirth()).isNull();
        assertThat(change.getVehicleType()).isEqualTo(VehicleType.BIKE);
        assertThat(change.getVehicleRegistrationNumber()).isEqualTo("TS09EB1111");
        assertThat(profile.getVehicleRegistrationNumber()).isEqualTo("TS09EA4521");
    }

    @Test
    void savingTheSameDetailsAgainCreatesNothing() {
        service.requestDetails(profile, "Lakshmi ", LocalDate.of(1995, 4, 2), VehicleType.BIKE, "TS09EA4521");

        verify(changes, never()).save(any());
    }

    @Test
    void aVehicleChangeCannotBeApprovedWithoutItsCertificate() {
        DriverProfileChangeEntity change = new DriverProfileChangeEntity(partner);
        change.requestDetails(null, null, VehicleType.BIKE, "TS09EB1111");
        UUID id = UUID.randomUUID();
        when(changes.findById(id)).thenReturn(Optional.of(change));

        var result = service.decide(id, true, UUID.randomUUID(), null);

        assertThat(result.error()).isEqualTo(DriverProfileError.RC_DOCUMENT_REQUIRED);
        assertThat(profile.getVehicleRegistrationNumber()).isEqualTo("TS09EA4521");
    }

    @Test
    void approvingAppliesTheChangeAndTellsHer() {
        DriverProfileChangeEntity change = new DriverProfileChangeEntity(partner);
        change.requestDetails("Lakshmi Devi", null, VehicleType.BIKE, "TS09EB1111");
        change.attachRcDocument("rc-new");
        change.requestPhoto("photo-new");
        UUID id = UUID.randomUUID();
        when(changes.findById(id)).thenReturn(Optional.of(change));

        var result = service.decide(id, true, UUID.randomUUID(), null);

        assertThat(result.isSuccess()).isTrue();
        assertThat(profile.getName()).isEqualTo("Lakshmi Devi");
        assertThat(profile.getVehicleRegistrationNumber()).isEqualTo("TS09EB1111");
        assertThat(profile.getProfilePhotoKey()).isEqualTo("photo-new");
        assertThat(change.getStatus()).isEqualTo(DriverProfileChangeEntity.Status.APPROVED);
        verify(storage).delete("photo-approved");
        verify(events).publish(any(DriverProfileChangeDecided.class));
    }

    @Test
    void turningDownNeedsAReasonAndChangesNothing() {
        DriverProfileChangeEntity change = new DriverProfileChangeEntity(partner);
        change.requestDetails("Someone Else", null, null, null);
        UUID id = UUID.randomUUID();
        when(changes.findById(id)).thenReturn(Optional.of(change));

        assertThat(service.decide(id, false, UUID.randomUUID(), "  ").error()).isEqualTo(DriverProfileError.DECISION_NOTE_REQUIRED);
        assertThat(service.decide(id, false, UUID.randomUUID(), "Name does not match your ID").isSuccess()).isTrue();
        assertThat(profile.getName()).isEqualTo("Lakshmi");
        assertThat(change.getDecisionNote()).isEqualTo("Name does not match your ID");
    }

    @Test
    void aCertificateNeedsAVehicleChange() {
        DriverProfileChangeEntity change = new DriverProfileChangeEntity(partner);
        change.requestDetails("Lakshmi Devi", null, null, null);
        when(changes.findFirstByAccountIdAndStatus(partner, DriverProfileChangeEntity.Status.PENDING)).thenReturn(Optional.of(change));

        var result = service.attachRcDocument(partner, mock(DocumentUpload.class));

        assertThat(result.error()).isEqualTo(DriverProfileError.VEHICLE_NOT_CHANGED);
        verify(storage, never()).store(eq(partner), any(), any());
    }
}
