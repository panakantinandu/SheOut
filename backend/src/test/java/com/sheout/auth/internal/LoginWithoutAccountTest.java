package com.sheout.auth.internal;

import com.sheout.auth.AccountRole;
import com.sheout.auth.internal.otp.OtpService;
import com.sheout.auth.internal.security.JwtService;
import com.sheout.sharedkernel.Result;
import com.sheout.sharedkernel.event.DomainEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The Login tab asks before creating an account: a number with no account in
 * this app is answered NO_ACCOUNT - only once the code is right - and the code
 * stays usable for "Create my account".
 */
class LoginWithoutAccountTest {

    private static final String NUMBER = "+919000000777";

    private AccountRepository accounts;
    private OtpService otp;
    private AuthService auth;

    @BeforeEach
    void setUp() {
        accounts = mock(AccountRepository.class);
        otp = mock(OtpService.class);
        auth = new AuthService(accounts, mock(SessionService.class), otp, mock(JwtService.class),
                mock(DomainEventPublisher.class), "test", false);
        when(accounts.findByPhoneNumberOrderByCreatedAtAsc(NUMBER)).thenReturn(List.of());
        when(accounts.save(any())).thenAnswer(call -> call.getArgument(0));
        when(otp.checkCode(NUMBER, AccountRole.CUSTOMER, "123456")).thenReturn(OtpService.VerificationOutcome.MATCHED);
        when(otp.checkCode(NUMBER, AccountRole.CUSTOMER, "000000")).thenReturn(OtpService.VerificationOutcome.MISMATCH);
    }

    @Test
    void loginWithANewNumberAsksInsteadOfCreatingAndKeepsTheCode() {
        Result<?, AuthError> result = auth.verifyOtp(NUMBER, "123456", AccountRole.CUSTOMER, "ua", false);

        assertThat(result.error()).isEqualTo(AuthError.NO_ACCOUNT);
        verify(accounts, never()).save(any());
        verify(otp, never()).consume(NUMBER, AccountRole.CUSTOMER);
    }

    @Test
    void aWrongCodeNeverRevealsWhetherTheNumberHasAnAccount() {
        Result<?, AuthError> result = auth.verifyOtp(NUMBER, "000000", AccountRole.CUSTOMER, "ua", false);

        assertThat(result.error()).isEqualTo(AuthError.OTP_CODE_MISMATCH);
        verify(accounts, never()).findByPhoneNumberOrderByCreatedAtAsc(NUMBER);
    }

    @Test
    void sayingYesCreatesTheAccountWithTheSameCodeAndSpendsIt() {
        Result<?, AuthError> result = auth.verifyOtp(NUMBER, "123456", AccountRole.CUSTOMER, "ua", true);

        assertThat(result.isSuccess()).isTrue();
        verify(accounts).save(any());
        verify(otp).consume(NUMBER, AccountRole.CUSTOMER);
    }

    @Test
    void anExistingAccountSignsInFromTheLoginTabAsBefore() {
        AccountEntity existing = new AccountEntity(NUMBER, AccountRole.CUSTOMER);
        when(accounts.findByPhoneNumberOrderByCreatedAtAsc(NUMBER)).thenReturn(List.of(existing));

        Result<?, AuthError> result = auth.verifyOtp(NUMBER, "123456", AccountRole.CUSTOMER, "ua", false);

        assertThat(result.isSuccess()).isTrue();
        verify(accounts, never()).save(any());
        verify(otp).consume(NUMBER, AccountRole.CUSTOMER);
    }
}
