package com.sheout.booking.internal;

import com.sheout.booking.BookingType;
import com.sheout.insurance.InsuranceApi;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** With cover required and none in force, rides are refused; deliveries never are, and without the rule nothing is. */
class RideInsuranceGateTest {

    private final InsuranceApi insurance = mock(InsuranceApi.class);

    @Test
    void ridesAreRefusedWithoutPassengerCoverWhenItIsRequired() {
        when(insurance.passengerCoverActive()).thenReturn(false);
        RideInsuranceGate gate = new RideInsuranceGate(insurance, true);

        assertThat(gate.refuses(BookingType.RIDE)).isTrue();
        assertThat(gate.refuses(BookingType.DELIVERY)).isFalse();
    }

    @Test
    void ridesGoAheadOnceCoverIsInForce() {
        when(insurance.passengerCoverActive()).thenReturn(true);

        assertThat(new RideInsuranceGate(insurance, true).refuses(BookingType.RIDE)).isFalse();
    }

    @Test
    void nothingIsRefusedWhenCoverIsNotRequired() {
        when(insurance.passengerCoverActive()).thenReturn(false);

        assertThat(new RideInsuranceGate(insurance, false).refuses(BookingType.RIDE)).isFalse();
    }
}
