package com.sheout.sharedkernel.devmode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DevModeTest {

    /** Every bypass configured; only the master switch varies. */
    private static DevMode everything(boolean enabled) {
        return new DevMode(enabled,
                "+919000000101:111111,+919000000102:222222",
                "+919000000001", "654321", "", "", "", "",
                "+91999999", "123456",
                true,
                "+919000000101", "+919000000102");
    }

    @Test
    void offMeansEveryBypassIsOffWhateverElseIsSet() {
        DevMode off = everything(false);
        assertThat(off.enabled()).isFalse();
        assertThat(off.fixedOtpCodeFor("+919000000101")).isNull();
        assertThat(off.fixedOtpCodeFor("+919000000001")).isNull();
        assertThat(off.fixedOtpCodeFor("+919999990001")).isNull();
        assertThat(off.logOtpCodes()).isFalse();
        assertThat(off.verifiedRiderBypassPhones()).isEmpty();
        assertThat(off.verifiedPartnerBypassPhones()).isEmpty();
        assertThat(off.isTestRider("+919000000101")).isFalse();
    }

    @Test
    void onEveryConfiguredBypassWorks() {
        DevMode on = everything(true);
        assertThat(on.fixedOtpCodeFor("+919000000101")).isEqualTo("111111");
        assertThat(on.fixedOtpCodeFor("+919000000001")).isEqualTo("654321");
        assertThat(on.logOtpCodes()).isTrue();
        assertThat(on.verifiedRiderBypassPhones()).containsExactly("+919000000101");
        assertThat(on.verifiedPartnerBypassPhones()).containsExactly("+919000000102");
        assertThat(on.isTestRider("+919000000101")).isTrue();
        assertThat(on.isTestPartner("+919000000101")).isFalse();
    }

    @Test
    void thePrefixCoversEveryNumberUnderItAndNothingElse() {
        DevMode on = everything(true);
        assertThat(on.fixedOtpCodeFor("+919999990001")).isEqualTo("123456");
        assertThat(on.fixedOtpCodeFor("+919999990002")).isEqualTo("123456");
        assertThat(on.fixedOtpCodeFor("+919999997654")).isEqualTo("123456");
        // A real number that merely shares the country code.
        assertThat(on.fixedOtpCodeFor("+919876543210")).isNull();
        // One digit short of the prefix is not under it.
        assertThat(on.fixedOtpCodeFor("+9199999")).isNull();
        // The prefix itself, with nothing after it, is not a number.
        assertThat(on.fixedOtpCodeFor("+91999999")).isNull();
    }

    @Test
    void aPrefixBroadEnoughToCatchRealPeopleIsRefused() {
        assertThat(DevMode.validPrefix("+91")).isNull();
        assertThat(DevMode.validPrefix("+9199")).isNull();
        assertThat(DevMode.validPrefix("9199999999")).isNull();
        assertThat(DevMode.validPrefix("+91 999999")).isNull();
        assertThat(DevMode.validPrefix("+91999999")).isEqualTo("+91999999");

        DevMode on = new DevMode(true, "", "", "", "", "", "", "", "+91", "123456", false, "", "");
        assertThat(on.fixedOtpCodeFor("+919876543210")).isNull();
    }

    @Test
    void aListedNumberKeepsItsOwnCodeInsideThePrefix() {
        DevMode on = new DevMode(true, "+919999990001:777777", "", "", "", "", "", "",
                "+91999999", "123456", false, "", "");
        assertThat(on.fixedOtpCodeFor("+919999990001")).isEqualTo("777777");
        assertThat(on.fixedOtpCodeFor("+919999990002")).isEqualTo("123456");
    }

    /** Test accounts are a list, and only fixed-code numbers count: a typo must not verify a stranger. */
    @Test
    void testAccountsAreAListOfFixedCodeNumbersOnly() {
        DevMode on = new DevMode(true, "", "", "", "", "", "", "",
                "+91999999", "123456", false,
                "+919999991001, +919999991002 +919876543210", "+919999992001,+919999992002");
        assertThat(on.verifiedRiderBypassPhones()).containsExactly("+919999991001", "+919999991002");
        assertThat(on.isTestRider("+919876543210")).isFalse();
        assertThat(on.verifiedPartnerBypassPhones()).containsExactly("+919999992001", "+919999992002");
        assertThat(on.isTestPartner("+919999992002")).isTrue();
    }
}
