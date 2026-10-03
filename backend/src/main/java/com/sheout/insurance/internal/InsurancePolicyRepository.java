package com.sheout.insurance.internal;

import com.sheout.insurance.PolicyKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface InsurancePolicyRepository extends JpaRepository<InsurancePolicyEntity, UUID> {

    List<InsurancePolicyEntity> findAllByOrderByActiveDescEffectiveFromDesc();

    List<InsurancePolicyEntity> findByKindAndActiveTrue(PolicyKind kind);

    List<InsurancePolicyEntity> findByActiveTrue();
}
