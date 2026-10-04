package com.sheout.staff.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A passkey in software, for tests: what a phone or laptop does when the
 * console calls navigator.credentials.create() or .get(). P-256 key, "none"
 * attestation, user present and verified, a counter that goes up.
 * <p>
 * It answers exactly the challenge it is given, for the origin it is told,
 * so a test can also answer for the wrong site or replay an old answer and
 * watch the server refuse.
 */
final class SoftPasskey {

    static final String ORIGIN = "http://localhost:8080";
    static final String RP_ID = "localhost";

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();

    private final KeyPair keys;
    private final byte[] credentialId = new byte[16];
    private final byte[] userHandle;
    private int counter;

    SoftPasskey(UUID staffId) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            this.keys = generator.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        new SecureRandom().nextBytes(credentialId);
        this.userHandle = ByteBuffer.allocate(16).putLong(staffId.getMostSignificantBits())
                .putLong(staffId.getLeastSignificantBits()).array();
    }

    /** The browser's answer to credentials.create(), as PublicKeyCredential JSON. */
    String register(JsonNode publicKey) throws Exception {
        byte[] clientData = clientData("webauthn.create", publicKey.get("challenge").asText(), ORIGIN);
        ByteArrayOutputStream auth = new ByteArrayOutputStream();
        auth.write(sha256(RP_ID.getBytes(StandardCharsets.UTF_8)));
        auth.write(0x01 | 0x04 | 0x40);                       // user present, user verified, credential data included
        auth.write(ByteBuffer.allocate(4).putInt(counter).array());
        auth.write(new byte[16]);                              // AAGUID: none
        auth.write(ByteBuffer.allocate(2).putShort((short) credentialId.length).array());
        auth.write(credentialId);
        auth.write(coseKey());
        Map<Object, Object> attestation = new LinkedHashMap<>();
        attestation.put("fmt", "none");
        attestation.put("attStmt", new LinkedHashMap<>());
        attestation.put("authData", auth.toByteArray());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("clientDataJSON", B64.encodeToString(clientData));
        response.put("attestationObject", B64.encodeToString(Cbor.encode(attestation)));
        response.put("transports", new String[]{"internal"});
        return credential(response);
    }

    /** The browser's answer to credentials.get(), for the given origin. */
    String sign(JsonNode publicKey, String origin) throws Exception {
        counter++;
        byte[] clientData = clientData("webauthn.get", publicKey.get("challenge").asText(), origin);
        ByteArrayOutputStream auth = new ByteArrayOutputStream();
        auth.write(sha256(RP_ID.getBytes(StandardCharsets.UTF_8)));
        auth.write(0x01 | 0x04);
        auth.write(ByteBuffer.allocate(4).putInt(counter).array());
        byte[] authData = auth.toByteArray();

        Signature ecdsa = Signature.getInstance("SHA256withECDSA");
        ecdsa.initSign(keys.getPrivate());
        ecdsa.update(authData);
        ecdsa.update(sha256(clientData));

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("clientDataJSON", B64.encodeToString(clientData));
        response.put("authenticatorData", B64.encodeToString(authData));
        response.put("signature", B64.encodeToString(ecdsa.sign()));
        response.put("userHandle", B64.encodeToString(userHandle));
        return credential(response);
    }

    String sign(JsonNode publicKey) throws Exception {
        return sign(publicKey, ORIGIN);
    }

    private String credential(Map<String, Object> response) throws Exception {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", B64.encodeToString(credentialId));
        out.put("rawId", B64.encodeToString(credentialId));
        out.put("type", "public-key");
        out.put("response", response);
        out.put("clientExtensionResults", new LinkedHashMap<>());
        return JSON.writeValueAsString(out);
    }

    private static byte[] clientData(String type, String challenge, String origin) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("type", type);
        data.put("challenge", challenge);
        data.put("origin", origin);
        data.put("crossOrigin", false);
        return JSON.writeValueAsBytes(data);
    }

    /** The public key as COSE: EC2, ES256, P-256, x, y. */
    private byte[] coseKey() {
        ECPublicKey pub = (ECPublicKey) keys.getPublic();
        Map<Object, Object> key = new LinkedHashMap<>();
        key.put(1, 2);
        key.put(3, -7);
        key.put(-1, 1);
        key.put(-2, unsigned32(pub.getW().getAffineX()));
        key.put(-3, unsigned32(pub.getW().getAffineY()));
        return Cbor.encode(key);
    }

    private static byte[] unsigned32(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] out = new byte[32];
        int copy = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - copy, out, 32 - copy, copy);
        return out;
    }

    private static byte[] sha256(byte[] in) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(in);
    }

    /** Just enough CBOR for an attestation object and a COSE key. */
    static final class Cbor {

        static byte[] encode(Object value) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            write(out, value);
            return out.toByteArray();
        }

        private static void write(ByteArrayOutputStream out, Object value) {
            if (value instanceof Integer i) {
                if (i >= 0) {
                    header(out, 0, i);
                } else {
                    header(out, 1, -1 - i);
                }
            } else if (value instanceof byte[] bytes) {
                header(out, 2, bytes.length);
                out.writeBytes(bytes);
            } else if (value instanceof String text) {
                byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                header(out, 3, bytes.length);
                out.writeBytes(bytes);
            } else if (value instanceof Map<?, ?> map) {
                header(out, 5, map.size());
                map.forEach((k, v) -> {
                    write(out, k);
                    write(out, v);
                });
            } else {
                throw new IllegalArgumentException("Not encodable: " + value);
            }
        }

        private static void header(ByteArrayOutputStream out, int major, long length) {
            if (length < 24) {
                out.write((major << 5) | (int) length);
            } else if (length < 256) {
                out.write((major << 5) | 24);
                out.write((int) length);
            } else {
                out.write((major << 5) | 25);
                out.write((int) (length >> 8));
                out.write((int) length);
            }
        }
    }
}
