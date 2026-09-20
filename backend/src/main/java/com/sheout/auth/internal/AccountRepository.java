package com.sheout.auth.internal;

import com.sheout.auth.AccountRole;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    /**
     * Addresses of accounts in a role, a page at a time, for an announcement.
     * <p>
     * An account has an email when she signed in with Google - it is how she
     * signed in, not something she typed into her profile - so this is where
     * most addresses actually are. Blocked accounts are left out: somebody
     * who has lost access to SheOut should not keep hearing from it.
     */
    @Query("select a.email from AccountEntity a where a.role = :role and a.email is not null "
            + "and a.email <> '' and a.blockedAt is null order by a.id")
    List<String> findEmailAddresses(@Param("role") AccountRole role, Pageable pageable);

    List<AccountEntity> findByEmailOrderByCreatedAtAsc(String email);

}
