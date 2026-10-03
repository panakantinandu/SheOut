package com.sheout.insurance.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface TripCoverageClaimRepository extends JpaRepository<TripCoverageClaimEntity, UUID> {

    List<TripCoverageClaimEntity> findByBookingId(UUID bookingId);
}
