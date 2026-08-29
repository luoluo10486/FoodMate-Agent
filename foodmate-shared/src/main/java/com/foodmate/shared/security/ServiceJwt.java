package com.foodmate.shared.security;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** 带严格声明校验的精简 Ed25519 服务间 JWT 实现。 */
public final class ServiceJwt {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    private ServiceJwt() {}

    public static String sign(
            String privateKeyBase64,
            String issuer,
            String audience,
            String scope,
            String kid,
            long ttlSeconds) {
        try {
            PrivateKey key =
                    KeyFactory.getInstance("Ed25519")
                            .generatePrivate(
                                    new PKCS8EncodedKeySpec(
                                            Base64.getDecoder().decode(privateKeyBase64)));
            ObjectNode header =
                    JSON.createObjectNode().put("alg", "EdDSA").put("typ", "JWT").put("kid", kid);
            Instant now = Instant.now();
            ObjectNode claims =
                    JSON.createObjectNode()
                            .put("iss", issuer)
                            .put("sub", issuer)
                            .put("aud", audience)
                            .put("scope", scope)
                            .put("iat", now.getEpochSecond())
                            .put("exp", now.plusSeconds(ttlSeconds).getEpochSecond())
                            .put("jti", UUID.randomUUID().toString());
            String unsigned = encoded(header) + "." + encoded(claims);
            Signature signature = Signature.getInstance("Ed25519");
            signature.initSign(key);
            signature.update(unsigned.getBytes(StandardCharsets.US_ASCII));
            return unsigned + "." + ENCODER.encodeToString(signature.sign());
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("unable to sign service JWT", exception);
        }
    }

    public static void verify(
            String token,
            String publicKeyBase64,
            String issuer,
            String audience,
            String requiredScope) {
        verify(
                token,
                new PublicKeyRing(Map.of("*", publicKeyBase64)),
                issuer,
                audience,
                requiredScope);
    }

    /**
     * Verifies a token against a key ring. The token's {@code kid} must select an exact key; the
     * wildcard is retained only for the legacy single-key configuration.
     */
    public static void verify(
            String token,
            PublicKeyRing publicKeys,
            String issuer,
            String audience,
            String requiredScope) {
        try {
            String[] parts = token == null ? new String[0] : token.split("\\.");
            if (parts.length != 3) throw invalid();
            JsonNode header =
                    JSON.readTree(new String(DECODER.decode(parts[0]), StandardCharsets.UTF_8));
            JsonNode claims =
                    JSON.readTree(new String(DECODER.decode(parts[1]), StandardCharsets.UTF_8));
            if (!"EdDSA".equals(header.path("alg").asText())
                    || !"JWT".equals(header.path("typ").asText())
                    || header.path("kid").asText().isBlank()) throw invalid();
            String keyBase64 =
                    publicKeys == null ? null : publicKeys.find(header.path("kid").asText());
            if (keyBase64 == null || keyBase64.isBlank()) throw invalid();
            PublicKey key =
                    KeyFactory.getInstance("Ed25519")
                            .generatePublic(
                                    new X509EncodedKeySpec(Base64.getDecoder().decode(keyBase64)));
            Signature signature = Signature.getInstance("Ed25519");
            signature.initVerify(key);
            signature.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
            if (!signature.verify(DECODER.decode(parts[2]))) throw invalid();
            if (!issuer.equals(claims.path("iss").asText())
                    || !audience.equals(claims.path("aud").asText())
                    || !hasScope(claims.path("scope").asText(), requiredScope)
                    || claims.path("exp").asLong(0) <= Instant.now().getEpochSecond()
                    || claims.path("jti").asText().isBlank()) throw invalid();
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (GeneralSecurityException
                | JsonProcessingException
                | IllegalArgumentException exception) {
            throw invalid();
        }
    }

    /** Parses {@code kid=base64Key,kid2=base64Key} while supporting the legacy single key. */
    public static PublicKeyRing parsePublicKeyRing(
            String entries, String legacyKid, String legacyKey) {
        Map<String, String> keys = new LinkedHashMap<>();
        if (entries != null && !entries.isBlank()) {
            for (String entry : entries.split("[,;]")) {
                String value = entry.trim();
                int separator = value.indexOf('=');
                if (separator <= 0 || separator == value.length() - 1)
                    throw new IllegalArgumentException("invalid service JWT key ring entry");
                String kid = value.substring(0, separator).trim();
                String key = value.substring(separator + 1).trim();
                if (kid.isBlank() || key.isBlank() || keys.putIfAbsent(kid, key) != null)
                    throw new IllegalArgumentException("invalid service JWT key ring entry");
            }
        } else if (legacyKey != null && !legacyKey.isBlank()) {
            keys.put(
                    legacyKid == null || legacyKid.isBlank() ? "*" : legacyKid.trim(),
                    legacyKey.trim());
        }
        return new PublicKeyRing(keys);
    }

    /** JWT 公钥环的强类型边界，避免 HTTP 控制器直接暴露通用 Map。 */
    public static final class PublicKeyRing {
        private final Map<String, String> values;

        private PublicKeyRing(Map<String, String> values) {
            this.values = Map.copyOf(values);
        }

        public boolean isEmpty() {
            return values.isEmpty();
        }

        public void forEachKey(Consumer<String> consumer) {
            values.values().forEach(consumer);
        }

        private String find(String kid) {
            String key = values.get(kid);
            return key == null ? values.get("*") : key;
        }
    }

    private static String encoded(JsonNode value) {
        try {
            return ENCODER.encodeToString(JSON.writeValueAsBytes(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean hasScope(String scopes, String required) {
        for (String scope : scopes.split(" ")) if (required.equals(scope)) return true;
        return false;
    }

    private static IllegalStateException invalid() {
        return new IllegalStateException("invalid service JWT");
    }
}
