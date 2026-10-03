package com.sheout.staff.internal;

import com.sheout.staff.StaffRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Addresses from the documentation ranges (RFC 5737, RFC 3849): never anybody's real network. */
class StaffIpAllowlistTest {

    private static StaffIpAllowlist owner(String list) {
        return new StaffIpAllowlist(list, "", "", "", "", "", "", "");
    }

    @Test
    void offWhenNothingIsSet() {
        StaffIpAllowlist none = owner("");
        assertThat(none.restricts(StaffRole.OWNER)).isFalse();
        assertThat(none.allows(StaffRole.OWNER, "198.51.100.7")).isTrue();
        assertThat(none.allows(StaffRole.OWNER, null)).isTrue();
    }

    @Test
    void rangesAndSingleAddressesInBothFamilies() {
        StaffIpAllowlist list = owner("203.0.113.0/24, 198.51.100.7, 2001:db8:10::/48");
        assertThat(list.allows(StaffRole.OWNER, "203.0.113.200")).isTrue();
        assertThat(list.allows(StaffRole.OWNER, "203.0.114.1")).isFalse();
        assertThat(list.allows(StaffRole.OWNER, "198.51.100.7")).isTrue();
        assertThat(list.allows(StaffRole.OWNER, "198.51.100.8")).isFalse();
        assertThat(list.allows(StaffRole.OWNER, "2001:db8:10:ffff::1")).isTrue();
        assertThat(list.allows(StaffRole.OWNER, "2001:db8:11::1")).isFalse();
        // An IPv4 address written the IPv6 way is still that IPv4 address.
        assertThat(list.allows(StaffRole.OWNER, "::ffff:203.0.113.9")).isTrue();
        // Only the role with a list is held to it.
        assertThat(list.allows(StaffRole.FINANCE, "192.0.2.1")).isTrue();
    }

    @Test
    void anythingThatIsNotANumericAddressIsRefusedNotLookedUp() {
        StaffIpAllowlist list = owner("203.0.113.0/24");
        assertThat(list.allows(StaffRole.OWNER, "localhost")).isFalse();
        assertThat(list.allows(StaffRole.OWNER, "")).isFalse();
        assertThat(list.allows(StaffRole.OWNER, null)).isFalse();
    }

    @Test
    void aListThatCannotBeReadStopsTheServerRatherThanMeaningAnywhere() {
        assertThatThrownBy(() -> owner("203.0.113.0/33")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> owner("office-network")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> owner("203.0.113.0/x")).isInstanceOf(IllegalStateException.class);
    }
}
