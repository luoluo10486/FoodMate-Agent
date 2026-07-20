package com.foodmate.gateway;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class ServiceJwtTest {
    @Test
    void signsAndVerifiesScopedEd25519Token() throws Exception {
        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        String token = ServiceJwt.sign(privateKey, "foodmate-control-plane", "foodmate-agent-runtime", "runtime:dispatch", "java-2026-01", 60);
        assertDoesNotThrow(() -> ServiceJwt.verify(token, publicKey, "foodmate-control-plane", "foodmate-agent-runtime", "runtime:dispatch"));
        assertThrows(IllegalStateException.class, () -> ServiceJwt.verify(token, publicKey, "foodmate-control-plane", "foodmate-agent-runtime", "runtime:cancel"));
    }
}
