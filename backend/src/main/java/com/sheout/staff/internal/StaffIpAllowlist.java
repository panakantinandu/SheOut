package com.sheout.staff.internal;

import com.sheout.staff.StaffRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Networks a role may use the console from, for the roles that hold the most
 * (owners, finance). OFF unless STAFF_IP_ALLOWLIST_&lt;ROLE&gt; is set; the
 * founder chose to keep it off for now (2026-10-03).
 * <p>
 * A list is comma-separated addresses or CIDR ranges, IPv4 or IPv6
 * ("203.0.113.0/24, 2001:db8::/32"). It is checked when she signs in (after
 * every answer is right, so it says nothing to someone guessing) and on every
 * request, so a session opened in the office cannot be carried to another
 * network. The address is the one ClientAddressResolver gives - behind
 * Cloudflare that is CF-Connecting-IP (CLIENT_IP_HEADER), which a client
 * cannot set.
 * <p>
 * A list that cannot be read stops the server starting: a typo that quietly
 * meant "anywhere" would be worse than no list.
 */
@Component
class StaffIpAllowlist {

    private static final Logger log = LoggerFactory.getLogger(StaffIpAllowlist.class);

    private final Map<StaffRole, List<Range>> lists = new EnumMap<>(StaffRole.class);

    StaffIpAllowlist(@Value("${sheout.staff.ip-allowlist.owner:}") String owner,
                     @Value("${sheout.staff.ip-allowlist.manager:}") String manager,
                     @Value("${sheout.staff.ip-allowlist.verification-agent:}") String verificationAgent,
                     @Value("${sheout.staff.ip-allowlist.support-agent:}") String supportAgent,
                     @Value("${sheout.staff.ip-allowlist.safety-responder:}") String safetyResponder,
                     @Value("${sheout.staff.ip-allowlist.finance:}") String finance,
                     @Value("${sheout.staff.ip-allowlist.marketplace-moderator:}") String marketplaceModerator,
                     @Value("${sheout.staff.ip-allowlist.auditor:}") String auditor) {
        put(StaffRole.OWNER, owner);
        put(StaffRole.MANAGER, manager);
        put(StaffRole.VERIFICATION_AGENT, verificationAgent);
        put(StaffRole.SUPPORT_AGENT, supportAgent);
        put(StaffRole.SAFETY_RESPONDER, safetyResponder);
        put(StaffRole.FINANCE, finance);
        put(StaffRole.MARKETPLACE_MODERATOR, marketplaceModerator);
        put(StaffRole.AUDITOR, auditor);
    }

    /** True when the role has no list, or the address is on it. */
    boolean allows(StaffRole role, String address) {
        List<Range> ranges = lists.get(role);
        if (ranges == null) {
            return true;
        }
        byte[] bytes = bytes(address);
        if (bytes == null) {
            return false;
        }
        BigInteger value = new BigInteger(1, bytes);
        boolean ipv6 = bytes.length == 16;
        return ranges.stream().anyMatch(r -> r.contains(value, ipv6));
    }

    boolean restricts(StaffRole role) {
        return lists.containsKey(role);
    }

    private void put(StaffRole role, String configured) {
        if (configured == null || configured.isBlank()) {
            return;
        }
        List<Range> ranges = new ArrayList<>();
        for (String entry : configured.split(",")) {
            if (!entry.isBlank()) {
                ranges.add(Range.of(entry.trim(), role));
            }
        }
        lists.put(role, List.copyOf(ranges));
        log.info("Staff IP allowlist on for {}: {} range(s)", role, ranges.size());
    }

    /**
     * A numeric address only - never a host name, which would be a DNS lookup
     * on every request. The family comes from the parsed bytes, not the text:
     * "::ffff:203.0.113.5" is an IPv4 address written the IPv6 way.
     */
    static byte[] bytes(String address) {
        if (address == null || address.isBlank() || !address.matches("[0-9A-Fa-f:.]+")) {
            return null;
        }
        try {
            return InetAddress.getByName(address).getAddress();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    record Range(BigInteger network, BigInteger mask, boolean ipv6) {

        static Range of(String text, StaffRole role) {
            String[] parts = text.split("/");
            byte[] raw = bytes(parts[0]);
            if (raw == null || parts.length > 2) {
                throw new IllegalStateException("STAFF_IP_ALLOWLIST_" + role + " has an entry that is not an address or range: " + text);
            }
            BigInteger base = new BigInteger(1, raw);
            boolean ipv6 = raw.length == 16;
            int bits = ipv6 ? 128 : 32;
            int prefix;
            try {
                prefix = parts.length == 2 ? Integer.parseInt(parts[1]) : bits;
            } catch (NumberFormatException e) {
                throw new IllegalStateException("STAFF_IP_ALLOWLIST_" + role + " has a bad prefix length: " + text);
            }
            if (prefix < 0 || prefix > bits) {
                throw new IllegalStateException("STAFF_IP_ALLOWLIST_" + role + " has a bad prefix length: " + text);
            }
            BigInteger all = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
            BigInteger mask = all.shiftRight(bits - prefix).shiftLeft(bits - prefix);
            return new Range(base.and(mask), mask, ipv6);
        }

        boolean contains(BigInteger address, boolean addressIsIpv6) {
            return addressIsIpv6 == ipv6 && address.and(mask).equals(network);
        }
    }
}
