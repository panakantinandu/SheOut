package com.sheout.auth.internal;

import com.sheout.auth.AccountBlock;
import com.sheout.auth.AccountRegistered;
import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.auth.AuthenticatedSession;
import com.sheout.auth.SessionRevocation;
import com.sheout.auth.internal.otp.OtpService;
import com.sheout.auth.internal.security.JwtService;
import com.sheout.privacy.AccountDeletionRequested;
import com.sheout.sharedkernel.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService implements AuthApi {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final AccountRepository accountRepository;
    private final SessionService sessions;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final DomainEventPublisher eventPublisher;
    private final String termsVersion;

    public AuthService(AccountRepository accountRepository,
                        SessionService sessions,
                        OtpService otpService,
                        JwtService jwtService,
                        DomainEventPublisher eventPublisher,
                        // The wording she agreed to. Bumped when the documents
                        // change, so an old acceptance is not read as consent
                        // to something she never saw.
                        @Value("${sheout.legal.terms-version:2026-09-20}") String termsVersion) {
        this.accountRepository = accountRepository;
        this.sessions = sessions;
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.eventPublisher = eventPublisher;
        this.termsVersion = termsVersion;
    }

    /**
     * Requests a code for phoneNumber. Answers the same way for every
     * number, registered or not - see the comment inside.
     */
    public Result<Void, AuthError> requestOtp(String phoneNumber, AccountRole role) {
        // Deliberately NO account lookup here any more.
        //
        // This used to return ROLE_MISMATCH when the number already belonged
        // to a different role, which made this endpoint an account-
        // enumeration oracle: an unregistered number answered 202 and a
        // registered one answered 409 naming its role, to anyone, for any
        // number, without ever proving they owned it. On a women's safety
        // app that is not a small leak - it tells a stranger whether a
        // particular woman has an account and whether she drives for us.
        //
        // The same reasoning is already written down on the customer app's
        // Login screen for the "already registered" notice. It belonged here
        // too and was missed. There is no role mismatch any more - each app
        // has its own account on a number (see verifyOtp) - and the one
        // refusal left, a blocked account, is given only after the code
        // proves the number is theirs.
        //
        // Rate limits are checked by AuthController before this is reached -
        // see OtpRateLimiter - because a refusal has to carry a Retry-After,
        // which a Result error cannot.
        boolean delivered = otpService.requestCode(phoneNumber, role);
        if (!delivered) {
            return Result.failure(AuthError.OTP_DELIVERY_FAILED);
        }
        return Result.success(null);
    }

    /**
     * Verifies a code and either logs into this app's account for the phone
     * number, or creates one (a combined signup/login flow - there is no
     * separate "signup" step).
     * <p>
     * ONE ACCOUNT PER APP. The account is looked up by number AND role, so a
     * number already used in the partner app signs up fresh in the rider
     * app, and the other way round. There used to be one account per number:
     * a partner's number entered in the rider app was sent a code, which was
     * taken, and only then refused as "registered under a different role" -
     * and trying again hit the resend cooldown. Nothing about the other app's
     * account is read or revealed here.
     * Transactional so the new account row and the AccountRegistered
     * listener's write (creating driver-verification's record) commit or
     * roll back together.
     */
    @Transactional
    public Result<AuthenticatedSession, AuthError> verifyOtp(String phoneNumber, String code, AccountRole role, String userAgent) {
        OtpService.VerificationOutcome outcome = otpService.verifyCode(phoneNumber, role, code);
        if (outcome == OtpService.VerificationOutcome.NOT_FOUND_OR_EXPIRED) {
            return Result.failure(AuthError.OTP_NOT_FOUND_OR_EXPIRED);
        }
        if (outcome == OtpService.VerificationOutcome.MISMATCH) {
            return Result.failure(AuthError.OTP_CODE_MISMATCH);
        }

        // A block is on the person, not on one app's account. Checked on
        // every account this number holds, so somebody blocked as a partner
        // cannot carry on as a rider - or sign up as a partner after being
        // blocked as a rider - by switching apps. After the code is verified,
        // so this cannot be used to learn whether a number is blocked.
        List<AccountEntity> onThisNumber = accountRepository.findByPhoneNumberOrderByCreatedAtAsc(phoneNumber);
        if (onThisNumber.stream().anyMatch(AccountEntity::isBlocked)) {
            return Result.failure(AuthError.ACCOUNT_BLOCKED);
        }

        Optional<AccountEntity> existing = onThisNumber.stream().filter(a -> a.getRole() == role).findFirst();
        boolean isNewAccount = existing.isEmpty();

        AccountEntity account;
        if (isNewAccount) {
            if (role == AccountRole.ADMIN) {
                return Result.failure(AuthError.ADMIN_SELF_SIGNUP_FORBIDDEN);
            }
            account = accountRepository.save(new AccountEntity(phoneNumber, role));
            eventPublisher.publish(new AccountRegistered(account.getId(), role));
        } else {
            account = existing.get();
        }

        String token = issueFor(account, userAgent);
        return Result.success(new AuthenticatedSession(token, account.getId(), account.getRole(), isNewAccount));
    }

    /**
     * A token tied to a fresh session, which is what makes it possible to
     * take it away again - see SessionService.
     */
    private String issueFor(AccountEntity account, String userAgent) {
        UUID sessionId = sessions.open(account.getId(), account.getRole(), userAgent);
        return jwtService.issue(account.getId(), account.getRole(), sessionId);
    }

    /**
     * Google's equivalent of verifyOtp: the caller (AuthController) has
     * already verified the ID token server-side and hands over only the
     * claims it trusts (email, name) - this method's job is purely account
     * resolution, same split of responsibility as OtpService verifying the
     * code vs. this method resolving the account from an already-verified
     * outcome.
     * <p>
     * accounts.email is written only here, by a Google sign-in, so an account
     * found by email is the one this Google identity created. If a future
     * flow ever lets a phone account gain an email some other way, that flow
     * must verify the address - otherwise this lookup would sign somebody
     * into an account she does not own. EMAIL_LINKED_TO_PHONE_ACCOUNT is kept
     * for that day.
     */
    @Transactional
    public Result<AuthenticatedSession, AuthError> verifyGoogleSignIn(String email, String name, AccountRole role, String userAgent) {
        // The rider app only. Partners and operators are phone and code, full
        // stop - see AuthError.GOOGLE_NOT_FOR_ROLE. Checked first, so no
        // account is looked up or created for any other role.
        if (role != AccountRole.CUSTOMER) {
            return Result.failure(AuthError.GOOGLE_NOT_FOR_ROLE);
        }
        // Same per-app rule as verifyOtp: the email identifies an account
        // only together with the app signing in, and a block on any of this
        // person's accounts holds in every app.
        List<AccountEntity> onThisEmail = accountRepository.findByEmailOrderByCreatedAtAsc(email);
        if (onThisEmail.stream().anyMatch(AccountEntity::isBlocked)) {
            return Result.failure(AuthError.ACCOUNT_BLOCKED);
        }
        Optional<AccountEntity> existing = onThisEmail.stream().filter(a -> a.getRole() == role).findFirst();

        // An account found by email here is always one this same Google
        // identity created: nothing else ever writes accounts.email. It used
        // to be refused when it had a phone number, on the theory that a
        // phone account had gained an email some other way - but a Google
        // rider is REQUIRED to add a phone number before her first booking
        // (AddPhone), so every real Google rider was locked out of "Continue
        // with Google" from her second sign-in on, and told to use a phone
        // number she had never signed in with. She is let in; the number she
        // added signs in to the same account.

        boolean isNewAccount = existing.isEmpty();

        AccountEntity account;
        if (isNewAccount) {
            if (role == AccountRole.ADMIN) {
                return Result.failure(AuthError.ADMIN_SELF_SIGNUP_FORBIDDEN);
            }
            account = accountRepository.save(AccountEntity.forGoogleSignIn(email, role));
            eventPublisher.publish(new AccountRegistered(account.getId(), role, name));
        } else {
            account = existing.get();
        }

        String token = issueFor(account, userAgent);
        return Result.success(new AuthenticatedSession(token, account.getId(), account.getRole(), isNewAccount));
    }

    /**
     * Sends a code to the number a Google-signup rider is adding - the same
     * code, store and sender as signing in with a number.
     * <p>
     * Nothing about the number is looked up here, for the reason requestOtp
     * gives: before the code proves she owns it, answering "that number is
     * already registered" would tell anyone which numbers have accounts.
     * Only the account asking is checked - that it is a rider account, and
     * that it has no number yet.
     */
    public Result<Void, AuthError> requestAddedPhone(UUID accountId, String phoneNumber) {
        Optional<AccountEntity> account = accountRepository.findById(accountId);
        if (account.isEmpty() || account.get().getRole() != AccountRole.CUSTOMER) {
            return Result.failure(AuthError.GOOGLE_NOT_FOR_ROLE);
        }
        if (account.get().getPhoneNumber() != null) {
            return Result.failure(AuthError.PHONE_ALREADY_SET);
        }
        return requestOtp(phoneNumber, AccountRole.CUSTOMER);
    }

    /**
     * Verifies the code and gives this account the number.
     * <p>
     * After the code is verified, and only then, the same checks signing in
     * makes: a number carrying a block on any account cannot be attached -
     * a block is on the person - and a number that already signs in to
     * another rider account is refused rather than moved or merged. Merging
     * two accounts would carry trips, payments and verification across
     * without anybody deciding it should.
     * <p>
     * Not @Transactional: the save runs in the repository's own transaction,
     * so losing a race for the number on the unique index can be caught and
     * answered. Inside a surrounding transaction the failed flush would mark
     * it rollback-only, and the answer would become a 500 at commit.
     */
    public Result<String, AuthError> verifyAddedPhone(UUID accountId, String phoneNumber, String code) {
        Optional<AccountEntity> found = accountRepository.findById(accountId);
        if (found.isEmpty() || found.get().getRole() != AccountRole.CUSTOMER) {
            return Result.failure(AuthError.GOOGLE_NOT_FOR_ROLE);
        }
        AccountEntity account = found.get();
        if (account.getPhoneNumber() != null) {
            return Result.failure(AuthError.PHONE_ALREADY_SET);
        }
        OtpService.VerificationOutcome outcome = otpService.verifyCode(phoneNumber, AccountRole.CUSTOMER, code);
        if (outcome == OtpService.VerificationOutcome.NOT_FOUND_OR_EXPIRED) {
            return Result.failure(AuthError.OTP_NOT_FOUND_OR_EXPIRED);
        }
        if (outcome == OtpService.VerificationOutcome.MISMATCH) {
            return Result.failure(AuthError.OTP_CODE_MISMATCH);
        }
        List<AccountEntity> onThisNumber = accountRepository.findByPhoneNumberOrderByCreatedAtAsc(phoneNumber);
        if (onThisNumber.stream().anyMatch(AccountEntity::isBlocked)) {
            return Result.failure(AuthError.ACCOUNT_BLOCKED);
        }
        if (onThisNumber.stream().anyMatch(a -> a.getRole() == AccountRole.CUSTOMER)) {
            return Result.failure(AuthError.PHONE_ALREADY_REGISTERED);
        }
        account.attachPhoneNumber(phoneNumber);
        try {
            // A rider account taking the same number between the check above
            // and this write fails on the unique index here.
            accountRepository.save(account);
        } catch (DataIntegrityViolationException raced) {
            return Result.failure(AuthError.PHONE_ALREADY_REGISTERED);
        }
        log.info("Phone number added to Google-signup account {}", accountId);
        return Result.success(phoneNumber);
    }

    @Override
    public Page<AccountSummary> searchAccounts(String text, java.util.Set<AccountRole> roles, Boolean blocked, Pageable pageable) {
        String needle = text == null || text.isBlank() ? null : text.trim().toLowerCase();
        java.util.Set<AccountRole> include = roles == null || roles.isEmpty()
                ? java.util.Set.of(AccountRole.values())
                : roles;
        Pageable sorted = org.springframework.data.domain.PageRequest.of(
                pageable.getPageNumber(), pageable.getPageSize(),
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "createdAt"));
        return accountRepository.findAll(AccountSpecs.matching(include, blocked, needle), sorted)
                .map(AuthService::toSummary);
    }

    @Override
    @Transactional
    public Optional<AccountSummary> blockAccount(UUID accountId, UUID adminAccountId, String reason) {
        return accountRepository.findById(accountId).map(account -> {
            account.block(adminAccountId, reason);
            accountRepository.save(account);
            // The point of blocking: access stops now. Without this the
            // phone already holding a token carried on for up to thirty days.
            int ended = sessions.revokeAll(accountId, SessionRevocation.ACCOUNT_BLOCKED);
            log.warn("Blocking account {} ended {} live session(s)", accountId, ended);
            // Worth a log line in its own right: this is the record an
            // operator will look for when asked why someone lost access,
            // and the row alone does not say it happened at a point in a
            // sequence of other things.
            log.warn("Account {} blocked by admin {}", accountId, adminAccountId);
            return toSummary(account);
        });
    }

    @Override
    @Transactional
    public Optional<AccountSummary> unblockAccount(UUID accountId) {
        return accountRepository.findById(accountId).map(account -> {
            account.unblock();
            accountRepository.save(account);
            log.warn("Account {} unblocked", accountId);
            return toSummary(account);
        });
    }

    @Override
    public Optional<AccountBlock> findBlockDetail(UUID accountId) {
        return accountRepository.findById(accountId)
                .filter(AccountEntity::isBlocked)
                .map(a -> new AccountBlock(a.getBlockedAt(), a.getBlockedBy(), a.getBlockReason()));
    }

    static AccountSummary toSummary(AccountEntity a) {
        return new AccountSummary(
                a.getId(), a.getPhoneNumber(), a.getEmail(), a.getRole(), a.getCreatedAt(), a.isBlocked());
    }

    /**
     * The auth half of an account deletion. See AccountEntity.markDeleted
     * for what is removed and why nothing of the phone number is kept.
     */
    @EventListener
    @Transactional
    public void onAccountDeletionRequested(AccountDeletionRequested event) {
        accountRepository.findById(event.accountId()).ifPresent(account -> {
            account.markDeleted();
            accountRepository.save(account);
            sessions.revokeAll(event.accountId(), SessionRevocation.ACCOUNT_DELETED);
        });
    }

    @Override
    @Transactional
    public void acceptTerms(UUID accountId) {
        accountRepository.findById(accountId).ifPresent(account -> {
            account.acceptTerms(termsVersion);
            accountRepository.save(account);
        });
    }

    @Override
    public boolean hasAcceptedTerms(UUID accountId) {
        return accountRepository.findById(accountId).map(AccountEntity::hasAcceptedTerms).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> findEmailAddresses(AccountRole role, int page, int size) {
        return accountRepository.findEmailAddresses(role, PageRequest.of(page, size));
    }

    @Override
    public boolean samePerson(UUID accountA, UUID accountB) {
        if (accountA.equals(accountB)) {
            return true;
        }
        Optional<AccountEntity> a = accountRepository.findById(accountA);
        Optional<AccountEntity> b = accountRepository.findById(accountB);
        if (a.isEmpty() || b.isEmpty()) {
            return false;
        }
        return sameNonNull(a.get().getPhoneNumber(), b.get().getPhoneNumber())
                || sameNonNull(a.get().getEmail(), b.get().getEmail());
    }

    private static boolean sameNonNull(String x, String y) {
        return x != null && x.equals(y);
    }

    @Override
    public Optional<AccountSummary> findAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .map(AuthService::toSummary);
    }

    @Override
    @Transactional
    public Optional<AccountSummary> grantAdminRole(String phoneNumber) {
        // A number can hold one account per app. An admin account already on
        // it is the answer; otherwise the number's first account is promoted,
        // which is the one this chose before accounts were per app.
        List<AccountEntity> onThisNumber = accountRepository.findByPhoneNumberOrderByCreatedAtAsc(phoneNumber);
        Optional<AccountEntity> target = onThisNumber.stream()
                .filter(a -> a.getRole() == AccountRole.ADMIN)
                .findFirst()
                .or(() -> onThisNumber.stream().findFirst());
        return target.map(account -> {
            if (account.getRole() != AccountRole.ADMIN) {
                account.promoteToAdmin();
                accountRepository.save(account);
            }
            return toSummary(account);
        });
    }
}
