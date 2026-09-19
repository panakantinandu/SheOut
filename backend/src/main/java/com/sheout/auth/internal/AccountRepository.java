package com.sheout.auth.internal;

import com.sheout.auth.AccountRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Specification support is for the ops console's account list - see AccountSpecs.
 * <p>
 * A phone number (or email) identifies an account only together with a role:
 * the same number can hold a rider account and a partner account, one per
 * app. See V26__one_account_per_app.sql.
 */
interface AccountRepository extends JpaRepository<AccountEntity, UUID>, JpaSpecificationExecutor<AccountEntity> {

    Optional<AccountEntity> findByPhoneNumberAndRole(String phoneNumber, AccountRole role);

    /** Every account on this number, oldest first - one per role at most. */
    List<AccountEntity> findByPhoneNumberOrderByCreatedAtAsc(String phoneNumber);

    Optional<AccountEntity> findByEmailAndRole(String email, AccountRole role);

    List<AccountEntity> findByEmailOrderByCreatedAtAsc(String email);

    /** True for an account that exists and has not been deleted. See AuthService.isActiveAccount. */
    boolean existsByIdAndDeletedAtIsNull(UUID id);
}
