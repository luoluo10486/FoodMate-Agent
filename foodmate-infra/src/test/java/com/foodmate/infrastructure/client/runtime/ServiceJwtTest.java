package com.foodmate.infrastructure.client.runtime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.foodmate.shared.security.ServiceJwt;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ServiceJwtTest {
    @Test
    void signsAndVerifiesScopedEd25519Token() throws Exception {
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        String token =
                ServiceJwt.sign(
                        privateKey,
                        "foodmate-control-plane",
                        "foodmate-agent-runtime",
                        "runtime:dispatch",
                        "java-2026-01",
                        60);
        assertDoesNotThrow(
                () ->
                        ServiceJwt.verify(
                                token,
                                publicKey,
                                "foodmate-control-plane",
                                "foodmate-agent-runtime",
                                "runtime:dispatch"));
        assertThrows(
                IllegalStateException.class,
                () ->
                        ServiceJwt.verify(
                                token,
                                publicKey,
                                "foodmate-control-plane",
                                "foodmate-agent-runtime",
                                "runtime:cancel"));
    }

    @Test
    void verifiesOldAndNewKeysDuringRotationAndRejectsUnknownKid() throws Exception {
        var oldKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        var newKeys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String oldPrivate = Base64.getEncoder().encodeToString(oldKeys.getPrivate().getEncoded());
        String oldPublic = Base64.getEncoder().encodeToString(oldKeys.getPublic().getEncoded());
        String newPublic = Base64.getEncoder().encodeToString(newKeys.getPublic().getEncoded());
        String oldToken =
                ServiceJwt.sign(
                        oldPrivate,
                        "foodmate-control-plane",
                        "foodmate-agent-runtime",
                        "runtime:dispatch",
                        "java-old",
                        60);

        assertDoesNotThrow(
                () ->
                        ServiceJwt.verify(
                                oldToken,
                                Map.of("java-old", oldPublic, "java-new", newPublic),
                                "foodmate-control-plane",
                                "foodmate-agent-runtime",
                                "runtime:dispatch"));

        String unknownKidToken =
                ServiceJwt.sign(
                        oldPrivate,
                        "foodmate-control-plane",
                        "foodmate-agent-runtime",
                        "runtime:dispatch",
                        "java-removed",
                        60);
        assertThrows(
                IllegalStateException.class,
                () ->
                        ServiceJwt.verify(
                                unknownKidToken,
                                Map.of("java-old", oldPublic, "java-new", newPublic),
                                "foodmate-control-plane",
                                "foodmate-agent-runtime",
                                "runtime:dispatch"));
    }

    @Test
    void parsesRotatingKeyRingAndLegacySingleKey() {
        assertEquals(
                Map.of("old", "old-key", "new", "new-key"),
                ServiceJwt.parsePublicKeyRing("old=old-key,new=new-key", "", ""));
        assertEquals(
                Map.of("legacy", "legacy-key"),
                ServiceJwt.parsePublicKeyRing("", "legacy", "legacy-key"));
        assertThrows(
                IllegalArgumentException.class,
                () -> ServiceJwt.parsePublicKeyRing("old=old-key,old=new-key", "", ""));
    }
}
