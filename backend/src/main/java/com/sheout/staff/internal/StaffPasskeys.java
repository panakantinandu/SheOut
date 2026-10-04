package com.sheout.staff.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.sheout.staff.StaffRole;
import com.yubico.webauthn.AssertionRequest;
import com.yubico.webauthn.AssertionResult;
import com.yubico.webauthn.CredentialRepository;
import com.yubico.webauthn.FinishAssertionOptions;
import com.yubico.webauthn.FinishRegistrationOptions;
import com.yubico.webauthn.RegisteredCredential;
import com.yubico.webauthn.RegistrationResult;
import com.yubico.webauthn.RelyingParty;
import com.yubico.webauthn.StartAssertionOptions;
import com.yubico.webauthn.StartRegistrationOptions;
import com.yubico.webauthn.data.AuthenticatorSelectionCriteria;
import com.yubico.webauthn.data.ByteArray;
import com.yubico.webauthn.data.PublicKeyCredential;
import com.yubico.webauthn.data.PublicKeyCredentialCreationOptions;
import com.yubico.webauthn.data.PublicKeyCredentialDescriptor;
import com.yubico.webauthn.data.RelyingPartyIdentity;
import com.yubico.webauthn.data.ResidentKeyRequirement;
import com.yubico.webauthn.data.UserIdentity;
import com.yubico.webauthn.data.UserVerificationRequirement;
import com.yubico.webauthn.exception.AssertionFailedException;
import com.yubico.webauthn.exception.RegistrationFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Staff passkeys (WebAuthn): registering one, and using one as the second
 * factor at sign-in and for step-up.
 * <p>
 * WHY. An authenticator code can be read off a phone and typed into a
 * look-alike page; a passkey cannot - the browser signs only for the real
 * console's address. That is the attack an owner, who can move money and
 * grant access, is worth the trouble of. So owners must have one
 * (sheout.staff.passkey.required-roles) and managers are asked to
 * (recommended-roles). An owner without one can sign in with her code but
 * can do nothing until she adds one; an owner with one must sign in with it.
 * Recovery codes still work for everyone - a lost laptop must not lock an
 * owner out for good.
 * <p>
 * SIGN-IN WITHOUT LEAKING. The sign-in challenge is issued before anyone says
 * who they are, with no list of allowed keys, so the page cannot be used to
 * learn which emails have passkeys: the browser offers the person's own
 * passkeys for this site (they are created as discoverable for this reason).
 * The signed answer then arrives with email and password, and must belong to
 * that member.
 * <p>
 * Every check of the signature, the origin, the challenge, user
 * verification and the counter is Yubico's library, not code here.
 * Usernames given to it are staff ids, not emails; the user handle is the
 * staff id's 16 bytes.
 */
@Service
class StaffPasskeys implements CredentialRepository {

    private static final Logger log = LoggerFactory.getLogger(StaffPasskeys.class);
    static final Duration CEREMONY_VALID_FOR = Duration.ofMinutes(5);

    enum Purpose { REGISTER, SIGN_IN, STEP_UP }

    /** What the browser is asked (JSON for navigator.credentials.create/get), and which ceremony it answers. */
    record Ceremony(UUID challengeId, String publicKeyJson) {
    }

    record Passkey(UUID id, String label, Instant createdAt, Instant lastUsedAt) {
    }

    private final JdbcTemplate jdbc;
    private final StaffMemberRepository members;
    private final RelyingParty relyingParty;
    private final Set<StaffRole> required;
    private final Set<StaffRole> recommended;

    StaffPasskeys(JdbcTemplate jdbc, StaffMemberRepository members, StaffSettings settings,
                  @Value("${sheout.staff.passkey.rp-id:}") String rpId,
                  @Value("${sheout.staff.passkey.origins:}") String origins,
                  @Value("${sheout.staff.passkey.required-roles:OWNER}") String requiredRoles,
                  @Value("${sheout.staff.passkey.recommended-roles:MANAGER}") String recommendedRoles) {
        this.jdbc = jdbc;
        this.members = members;
        URI console = URI.create(settings.consoleUrl);
        String id = rpId.isBlank() ? console.getHost() : rpId.trim();
        Set<String> allowed = origins.isBlank()
                ? Set.of(console.getScheme() + "://" + console.getAuthority())
                : Arrays.stream(origins.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        this.relyingParty = RelyingParty.builder()
                .identity(RelyingPartyIdentity.builder().id(id).name("SheOut console").build())
                .credentialRepository(this)
                .origins(allowed)
                // No attestation is asked for: which make of key it is does not matter here, only that it is hers.
                .allowUntrustedAttestation(true)
                .build();
        this.required = roles(requiredRoles);
        this.recommended = roles(recommendedRoles);
    }

    private static Set<StaffRole> roles(String csv) {
        Set<StaffRole> out = EnumSet.noneOf(StaffRole.class);
        for (String part : csv.split(",")) {
            if (!part.isBlank()) {
                out.add(StaffRole.valueOf(part.trim().toUpperCase(Locale.ROOT)));
            }
        }
        return out;
    }

    boolean required(StaffRole role) {
        return required.contains(role);
    }

    boolean recommended(StaffRole role) {
        return recommended.contains(role);
    }

    // ---- her passkeys ----------------------------------------------------------

    @Transactional(readOnly = true)
    List<Passkey> list(UUID staffId) {
        return jdbc.query("select id, label, created_at, last_used_at from staff_passkeys where staff_id = ? order by created_at",
                (rs, n) -> new Passkey(rs.getObject(1, UUID.class), rs.getString(2), rs.getTimestamp(3).toInstant(),
                        rs.getTimestamp(4) == null ? null : rs.getTimestamp(4).toInstant()), staffId);
    }

    @Transactional(readOnly = true)
    int count(UUID staffId) {
        Integer n = jdbc.queryForObject("select count(*) from staff_passkeys where staff_id = ?", Integer.class, staffId);
        return n == null ? 0 : n;
    }

    @Transactional
    boolean remove(UUID staffId, UUID passkeyId) {
        return jdbc.update("delete from staff_passkeys where id = ? and staff_id = ?", passkeyId, staffId) == 1;
    }

    /** A second-factor reset: her passkeys go with her authenticator. */
    @Transactional
    void removeAll(UUID staffId) {
        jdbc.update("delete from staff_passkeys where staff_id = ?", staffId);
    }

    // ---- registering -------------------------------------------------------------

    @Transactional
    Ceremony startRegistration(StaffMemberEntity member) {
        PublicKeyCredentialCreationOptions options = relyingParty.startRegistration(StartRegistrationOptions.builder()
                .user(UserIdentity.builder()
                        .name(member.getEmail())
                        .displayName(member.getDisplayName())
                        .id(handle(member.getId()))
                        .build())
                .authenticatorSelection(AuthenticatorSelectionCriteria.builder()
                        .residentKey(ResidentKeyRequirement.REQUIRED)
                        .userVerification(UserVerificationRequirement.REQUIRED)
                        .build())
                .timeout(CEREMONY_VALID_FOR.toMillis())
                .build());
        try {
            return new Ceremony(store(member.getId(), Purpose.REGISTER, options.toJson()), options.toCredentialsCreateJson());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Passkey options did not serialise", e);
        }
    }

    /** Empty when the answer is not a valid new passkey for this challenge and this person. */
    @Transactional
    Optional<Passkey> finishRegistration(StaffMemberEntity member, UUID challengeId, String credentialJson, String label) {
        Optional<String> request = consume(challengeId, Purpose.REGISTER, member.getId());
        if (request.isEmpty()) {
            return Optional.empty();
        }
        try {
            RegistrationResult result = relyingParty.finishRegistration(FinishRegistrationOptions.builder()
                    .request(PublicKeyCredentialCreationOptions.fromJson(request.get()))
                    .response(PublicKeyCredential.parseRegistrationResponseJson(credentialJson))
                    .build());
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            jdbc.update("insert into staff_passkeys (id, staff_id, credential_id, public_key_cose, signature_count, label, created_at)"
                            + " values (?, ?, ?, ?, ?, ?, ?)", id, member.getId(), result.getKeyId().getId().getBase64Url(),
                    result.getPublicKeyCose().getBase64Url(), result.getSignatureCount(), label, Timestamp.from(now));
            return Optional.of(new Passkey(id, label, now, null));
        } catch (RegistrationFailedException | IOException | RuntimeException e) {
            log.info("Passkey registration refused for staff {}: {}", member.getId(), e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    // ---- using one -----------------------------------------------------------------

    /** Sign-in: before anyone is known, no keys listed (see the class comment). */
    @Transactional
    Ceremony startSignIn() {
        return startAssertion(null, StartAssertionOptions.builder()
                .userVerification(UserVerificationRequirement.REQUIRED)
                .timeout(CEREMONY_VALID_FOR.toMillis())
                .build(), Purpose.SIGN_IN);
    }

    /** Step-up: her own keys only. */
    @Transactional
    Ceremony startStepUp(StaffMemberEntity member) {
        return startAssertion(member.getId(), StartAssertionOptions.builder()
                .username(member.getId().toString())
                .userVerification(UserVerificationRequirement.REQUIRED)
                .timeout(CEREMONY_VALID_FOR.toMillis())
                .build(), Purpose.STEP_UP);
    }

    private Ceremony startAssertion(UUID staffId, StartAssertionOptions options, Purpose purpose) {
        AssertionRequest request = relyingParty.startAssertion(options);
        try {
            return new Ceremony(store(staffId, purpose, request.toJson()), request.toCredentialsGetJson());
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Passkey options did not serialise", e);
        }
    }

    /**
     * Whether this answer is a valid signature, for the challenge issued, by
     * one of THIS member's passkeys. On success the key's counter and last use
     * are updated.
     */
    @Transactional
    boolean verify(StaffMemberEntity member, Purpose purpose, UUID challengeId, String credentialJson) {
        Optional<String> request = consume(challengeId, purpose, purpose == Purpose.SIGN_IN ? null : member.getId());
        if (request.isEmpty()) {
            return false;
        }
        try {
            AssertionResult result = relyingParty.finishAssertion(FinishAssertionOptions.builder()
                    .request(AssertionRequest.fromJson(request.get()))
                    .response(PublicKeyCredential.parseAssertionResponseJson(credentialJson))
                    .build());
            if (!result.isSuccess() || !member.getId().toString().equals(result.getUsername())) {
                return false;
            }
            jdbc.update("update staff_passkeys set signature_count = ?, last_used_at = ? where credential_id = ?",
                    result.getSignatureCount(), Timestamp.from(Instant.now()), result.getCredentialId().getBase64Url());
            return true;
        } catch (AssertionFailedException | IOException | RuntimeException e) {
            log.info("Passkey refused for staff {} ({}): {}", member.getId(), purpose, e.getClass().getSimpleName());
            return false;
        }
    }

    // ---- ceremonies --------------------------------------------------------------

    private UUID store(UUID staffId, Purpose purpose, String requestJson) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into staff_passkey_challenges (id, staff_id, purpose, request_json, expires_at) values (?, ?, ?, ?, ?)",
                id, staffId, purpose.name(), requestJson, Timestamp.from(Instant.now().plus(CEREMONY_VALID_FOR)));
        return id;
    }

    /** Taken once: a second answer to the same challenge finds nothing. */
    private Optional<String> consume(UUID challengeId, Purpose purpose, UUID staffId) {
        if (challengeId == null) {
            return Optional.empty();
        }
        List<String> found = jdbc.query("delete from staff_passkey_challenges where id = ? and purpose = ? and expires_at > ?"
                        + " and (staff_id is not distinct from ?) returning request_json",
                (rs, n) -> rs.getString(1), challengeId, purpose.name(), Timestamp.from(Instant.now()), staffId);
        return found.stream().findFirst();
    }

    @Scheduled(cron = "${sheout.staff.passkey.sweep-cron:0 */15 * * * *}")
    @Transactional
    public void sweepExpired() {
        jdbc.update("delete from staff_passkey_challenges where expires_at < ?", Timestamp.from(Instant.now()));
    }

    // ---- CredentialRepository, for the library ----------------------------------

    static ByteArray handle(UUID staffId) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(staffId.getMostSignificantBits()).putLong(staffId.getLeastSignificantBits());
        return new ByteArray(buffer.array());
    }

    private static Optional<UUID> staffId(ByteArray handle) {
        if (handle == null || handle.size() != 16) {
            return Optional.empty();
        }
        ByteBuffer buffer = ByteBuffer.wrap(handle.getBytes());
        return Optional.of(new UUID(buffer.getLong(), buffer.getLong()));
    }

    private static Optional<UUID> parse(String username) {
        try {
            return Optional.of(UUID.fromString(username));
        } catch (IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }

    @Override
    public Set<PublicKeyCredentialDescriptor> getCredentialIdsForUsername(String username) {
        return parse(username).map(id -> new HashSet<>(jdbc.query("select credential_id from staff_passkeys where staff_id = ?",
                (rs, n) -> descriptor(rs.getString(1)), id))).orElseGet(HashSet::new);
    }

    @Override
    public Optional<ByteArray> getUserHandleForUsername(String username) {
        return parse(username).filter(members::existsById).map(StaffPasskeys::handle);
    }

    @Override
    public Optional<String> getUsernameForUserHandle(ByteArray userHandle) {
        return staffId(userHandle).filter(members::existsById).map(UUID::toString);
    }

    @Override
    public Optional<RegisteredCredential> lookup(ByteArray credentialId, ByteArray userHandle) {
        return staffId(userHandle).flatMap(staff -> jdbc.query(
                "select public_key_cose, signature_count from staff_passkeys where credential_id = ? and staff_id = ?",
                (rs, n) -> RegisteredCredential.builder()
                        .credentialId(credentialId)
                        .userHandle(userHandle)
                        .publicKeyCose(fromBase64Url(rs.getString(1)))
                        .signatureCount(rs.getLong(2))
                        .build(), credentialId.getBase64Url(), staff).stream().findFirst());
    }

    @Override
    public Set<RegisteredCredential> lookupAll(ByteArray credentialId) {
        return new HashSet<>(jdbc.query("select staff_id, public_key_cose, signature_count from staff_passkeys where credential_id = ?",
                (rs, n) -> RegisteredCredential.builder()
                        .credentialId(credentialId)
                        .userHandle(handle(rs.getObject(1, UUID.class)))
                        .publicKeyCose(fromBase64Url(rs.getString(2)))
                        .signatureCount(rs.getLong(3))
                        .build(), credentialId.getBase64Url()));
    }

    private static PublicKeyCredentialDescriptor descriptor(String base64Url) {
        return PublicKeyCredentialDescriptor.builder().id(fromBase64Url(base64Url)).build();
    }

    private static ByteArray fromBase64Url(String value) {
        try {
            return ByteArray.fromBase64Url(value);
        } catch (com.yubico.webauthn.data.exception.Base64UrlException e) {
            throw new IllegalStateException("Stored passkey value is not base64url", e);
        }
    }
}
