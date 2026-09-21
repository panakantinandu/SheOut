package com.sheout.auth.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.internal.otp.OtpService;
import com.sheout.auth.internal.security.JwtService;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The two rules that keep every account reachable by phone: Google sign-in
 * is for the rider app only, and a Google-signup rider's number is attached
 * only once a code proves it is hers, and never taken from another account.
 */
class AddedPhoneAndGoogleRoleTest {

    private static final String NUMBER = "+919000000555";

    private AccountRepository accounts;
    private OtpService otp;
    private AuthService auth;
    private UUID googleAccountId;
    private AccountEntity googleAccount;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        otp = mock(OtpService.class);
        auth = new AuthService(accounts, mock(SessionService.class), otp, mock(JwtService.class),
                mock(DomainEventPublisher.class), "test");
        googleAccountId = UUID.randomUUID();
        googleAccount = AccountEntity.forGoogleSignIn("rider@example.com", AccountRole.CUSTOMER);
        when(accounts.findById(googleAccountId)).thenReturn(Optional.of(googleAccount));
        when(accounts.findByPhoneNumberOrderByCreatedAtAsc(NUMBER)).thenReturn(List.of());
        when(otp.verifyCode(NUMBER, AccountRole.CUSTOMER, "123456")).thenReturn(OtpService.VerificationOutcome.MATCHED);
        when(otp.verifyCode(NUMBER, AccountRole.CUSTOMER, "000000")).thenReturn(OtpService.VerificationOutcome.MISMATCH);
    }

    @Test
    void googleSignInIsRefusedForPartnersWithoutTouchingAccounts() {
        Result<?, AuthError> result = auth.verifyGoogleSignIn("partner@example.com", "P", AccountRole.DRIVER, "ua");
        assertThat(result.error()).isEqualTo(AuthError.GOOGLE_NOT_FOR_ROLE);
        verifyNoInteractions(accounts);
    }

    @Test
    void googleSignInIsRefusedForOperators() {
        Result<?, AuthError> result = auth.verifyGoogleSignIn("ops@example.com", "O", AccountRole.ADMIN, "ua");
        assertThat(result.error()).isEqualTo(AuthError.GOOGLE_NOT_FOR_ROLE);
        verifyNoInteractions(accounts);
    }

    @Test
    void aRightCodeAttachesTheNumber() {
        Result<String, AuthError> result = auth.verifyAddedPhone(googleAccountId, NUMBER, "123456");
        assertThat(result.isSuccess()).isTrue();
        assertThat(googleAccount.getPhoneNumber()).isEqualTo(NUMBER);
        verify(accounts).save(googleAccount);
    }

    @Test
    void aWrongCodeAttachesNothing() {
        Result<String, AuthError> result = auth.verifyAddedPhone(googleAccountId, NUMBER, "000000");
        assertThat(result.error()).isEqualTo(AuthError.OTP_CODE_MISMATCH);
        assertThat(googleAccount.getPhoneNumber()).isNull();
        verify(accounts, never()).save(any());
    }

    @Test
    void aNumberThatAlreadySignsInToAnotherRiderAccountIsNotTaken() {
        when(accounts.findByPhoneNumberOrderByCreatedAtAsc(NUMBER))
                .thenReturn(List.of(new AccountEntity(NUMBER, AccountRole.CUSTOMER)));
        Result<String, AuthError> result = auth.verifyAddedPhone(googleAccountId, NUMBER, "123456");
        assertThat(result.error()).isEqualTo(AuthError.PHONE_ALREADY_REGISTERED);
        assertThat(googleAccount.getPhoneNumber()).isNull();
        verify(accounts, never()).save(any());
    }

    @Test
    void theSameWomansPartnerAccountDoesNotStopHerAddingItAsARider() {
        // One account per app on a number - see verifyOtp. Her partner
        // account on this number is not a rider account.
        when(accounts.findByPhoneNumberOrderByCreatedAtAsc(NUMBER))
                .thenReturn(List.of(new AccountEntity(NUMBER, AccountRole.DRIVER)));
        assertThat(auth.verifyAddedPhone(googleAccountId, NUMBER, "123456").isSuccess()).isTrue();
    }

    @Test
    void aNumberCarryingABlockIsRefused() {
        AccountEntity blockedPartner = new AccountEntity(NUMBER, AccountRole.DRIVER);
        blockedPartner.block(UUID.randomUUID(), "test");
        when(accounts.findByPhoneNumberOrderByCreatedAtAsc(NUMBER)).thenReturn(List.of(blockedPartner));
        assertThat(auth.verifyAddedPhone(googleAccountId, NUMBER, "123456").error()).isEqualTo(AuthError.ACCOUNT_BLOCKED);
        assertThat(googleAccount.getPhoneNumber()).isNull();
    }

    @Test
    void anAccountThatHasANumberCannotReplaceItHere() {
        UUID phoneAccountId = UUID.randomUUID();
        when(accounts.findById(phoneAccountId)).thenReturn(Optional.of(new AccountEntity("+919000000111", AccountRole.CUSTOMER)));
        assertThat(auth.verifyAddedPhone(phoneAccountId, NUMBER, "123456").error()).isEqualTo(AuthError.PHONE_ALREADY_SET);
        // The code is not even checked, so it is not used up either.
        verify(otp, never()).verifyCode(anyString(), any(), anyString());
    }

    @Test
    void losingARaceForTheNumberIsAnsweredNotThrown() {
        when(accounts.save(googleAccount)).thenThrow(new DataIntegrityViolationException("uq_accounts_phone_role"));
        assertThat(auth.verifyAddedPhone(googleAccountId, NUMBER, "123456").error()).isEqualTo(AuthError.PHONE_ALREADY_REGISTERED);
    }

    @Test
    void noCodeIsSentForAPartnerAccount() {
        UUID partnerId = UUID.randomUUID();
        when(accounts.findById(partnerId)).thenReturn(Optional.of(new AccountEntity("+919000000222", AccountRole.DRIVER)));
        assertThat(auth.requestAddedPhone(partnerId, NUMBER).error()).isEqualTo(AuthError.GOOGLE_NOT_FOR_ROLE);
        verify(otp, never()).requestCode(anyString(), any());
    }

    @Test
    void aCodeIsSentForAGoogleRiderWithNoNumber() {
        when(otp.requestCode(NUMBER, AccountRole.CUSTOMER)).thenReturn(true);
        assertThat(auth.requestAddedPhone(googleAccountId, NUMBER).isSuccess()).isTrue();
        verify(otp).requestCode(NUMBER, AccountRole.CUSTOMER);
    }
}
