package com.sheout.driververification.internal.web;

import com.sheout.auth.AccountRole;
import com.sheout.auth.CurrentAccount;
import com.sheout.auth.CurrentAccountContext;
import com.sheout.driververification.internal.CourtScreeningError;
import com.sheout.driververification.internal.CourtScreeningService;
import com.sheout.sharedkernel.web.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The partner side answers 404 to anyone who is not a partner, and to a partner with nothing to see. */
class CourtScreeningControllerTest {

    private final CourtScreeningService service = mock(CourtScreeningService.class);
    private final CourtScreeningController controller = new CourtScreeningController(service);

    @AfterEach
    void signOut() {
        CurrentAccountContext.clear();
    }

    @Test
    void aCustomerGets404() {
        CurrentAccountContext.set(new CurrentAccount(UUID.randomUUID(), AccountRole.CUSTOMER, UUID.randomUUID()));
        assertThatThrownBy(controller::mine).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void aPartnerWithNoScreeningOrWithTheFeatureOffGets404() {
        CurrentAccountContext.set(new CurrentAccount(UUID.randomUUID(), AccountRole.DRIVER, UUID.randomUUID()));
        when(service.forPartner(any())).thenReturn(Optional.empty());
        assertThatThrownBy(controller::mine).isInstanceOfSatisfying(ApiException.class,
                e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void everyRefusalMapsToAStatusAndACode() {
        for (CourtScreeningError error : CourtScreeningError.values()) {
            ApiException e = AdminCourtScreeningController.toApiException(error);
            assertThat(e.status().is4xxClientError()).as(error.name()).isTrue();
        }
        assertThat(AdminCourtScreeningController.toApiException(CourtScreeningError.NOT_FOUND).status()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
