package com.sheout.auth.internal;

import com.sheout.auth.AccountRegistered;
import com.sheout.auth.AccountRole;
import com.sheout.auth.AccountSummary;
import com.sheout.auth.AuthApi;
import com.sheout.auth.AuthenticatedSession;
import com.sheout.auth.internal.otp.OtpService;
import com.sheout.auth.internal.security.JwtService;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class AuthService implements AuthApi {

    private final AccountRepository accountRepository;
    private final OtpService otpService;
    private final JwtService jwtService;
    private final DomainEventPublisher eventPublisher;

    public AuthService(AccountRepository accountRepository,
                        OtpService otpService,
                        JwtService jwtService,
                        DomainEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.otpService = otpService;
        this.jwtService = jwtService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Requests a code for phoneNumber. If an account already exists for
     * this phone number under a different role, refuses up front rather
     * than issuing a code that can never successfully verify.
     */
    public Result<Void, AuthError> requestOtp(String phoneNumber, AccountRole role) {
        Optional<AccountEntity> existing = accountRepository.findByPhoneNumber(phoneNumber);
        if (existing.isPresent() && existing.get().getRole() != role) {
            return Result.failure(AuthError.ROLE_MISMATCH);
        }
        boolean delivered = otpService.requestCode(phoneNumber);
        if (!delivered) {
            return Result.failure(AuthError.OTP_DELIVERY_FAILED);
        }
        return Result.success(null);
    }

    /**
     * Verifies a code and either logs into the existing account for this
     * phone number, or creates a new one with the given role (this is a
     * combined signup/login flow - there is no separate "signup" step).
     * Transactional so the new account row and the AccountRegistered
     * listener's write (creating driver-verification's record) commit or
     * roll back together.
     */
    @Transactional
    public Result<AuthenticatedSession, AuthError> verifyOtp(String phoneNumber, String code, AccountRole role) {
        OtpService.VerificationOutcome outcome = otpService.verifyCode(phoneNumber, code);
        if (outcome == OtpService.VerificationOutcome.NOT_FOUND_OR_EXPIRED) {
            return Result.failure(AuthError.OTP_NOT_FOUND_OR_EXPIRED);
        }
        if (outcome == OtpService.VerificationOutcome.MISMATCH) {
            return Result.failure(AuthError.OTP_CODE_MISMATCH);
        }

        Optional<AccountEntity> existing = accountRepository.findByPhoneNumber(phoneNumber);
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
            if (account.getRole() != role) {
                return Result.failure(AuthError.ROLE_MISMATCH);
            }
        }

        String token = jwtService.issue(account.getId(), account.getRole());
        return Result.success(new AuthenticatedSession(token, account.getId(), account.getRole(), isNewAccount));
    }

    /**
     * Google's equivalent of verifyOtp: the caller (AuthController) has
     * already verified the ID token server-side and hands over only the
     * claims it trusts (email, name) - this method's job is purely account
     * resolution, same split of responsibility as OtpService verifying the
     * code vs. this method resolving the account from an already-verified
     * outcome.
     * <p>
     * ACCOUNT LINKING: explicitly refused, not silently done. If the
     * matched account also has a phone number on file, this is a deliberate
     * "don't merge auth methods without the account owner's knowledge"
     * refusal (EMAIL_LINKED_TO_PHONE_ACCOUNT) rather than logging into it -
     * the frontend shows a message directing them to sign in with phone
     * instead. NOTE: no current flow can actually reach that branch today -
     * phone+OTP signup never collects an email at all (see AccountEntity/V4
     * migration), so a phone account's email column is always null, and
     * this check can only ever match a *previous* Google sign-in. It's
     * built now so the right thing happens automatically the moment some
     * future flow (e.g. a "add email" profile field) makes it reachable,
     * rather than needing to remember to add this check later.
     */
    @Transactional
    public Result<AuthenticatedSession, AuthError> verifyGoogleSignIn(String email, String name, AccountRole role) {
        Optional<AccountEntity> existing = accountRepository.findByEmail(email);

        if (existing.isPresent() && existing.get().getPhoneNumber() != null) {
            return Result.failure(AuthError.EMAIL_LINKED_TO_PHONE_ACCOUNT);
        }

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
            if (account.getRole() != role) {
                return Result.failure(AuthError.ROLE_MISMATCH);
            }
        }

        String token = jwtService.issue(account.getId(), account.getRole());
        return Result.success(new AuthenticatedSession(token, account.getId(), account.getRole(), isNewAccount));
    }

    @Override
    public Optional<AccountSummary> findAccount(UUID accountId) {
        return accountRepository.findById(accountId)
                .map(a -> new AccountSummary(a.getId(), a.getPhoneNumber(), a.getEmail(), a.getRole(), a.getCreatedAt()));
    }

    @Override
    @Transactional
    public Optional<AccountSummary> grantAdminRole(String phoneNumber) {
        return accountRepository.findByPhoneNumber(phoneNumber).map(account -> {
            if (account.getRole() != AccountRole.ADMIN) {
                account.promoteToAdmin();
                accountRepository.save(account);
            }
            return new AccountSummary(
                    account.getId(), account.getPhoneNumber(), account.getEmail(),
                    account.getRole(), account.getCreatedAt());
        });
    }
}
