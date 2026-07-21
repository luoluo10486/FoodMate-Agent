package com.foodmate.bootstrap;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class M03ConfigurationValidatorTest {
    @Test
    void productionRejectsMissingDatabaseConfiguration() {
        MockEnvironment environment = base("prod");
        assertThrows(IllegalStateException.class, () -> new M03ConfigurationValidator(environment).validate());
    }

    @Test
    void productionRejectsInsecureCookieAndMissingRuntimeKeys() throws Exception {
        MockEnvironment environment = base("prod")
                .withProperty("spring.datasource.url", "jdbc:postgresql://db.internal:5432/foodmate")
                .withProperty("spring.datasource.username", "foodmate")
                .withProperty("spring.datasource.password", "strong-password")
                .withProperty("foodmate.security.cookie-secure", "false");
        assertThrows(IllegalStateException.class, () -> new M03ConfigurationValidator(environment).validate());

        var keys = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(keys.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(keys.getPublic().getEncoded());
        environment.withProperty("foodmate.security.cookie-secure", "true")
                .withProperty("foodmate.runtime.service-jwt.java-private-key", privateKey)
                .withProperty("foodmate.runtime.service-jwt.java-public-key", publicKey)
                .withProperty("foodmate.runtime.service-jwt.python-public-key", publicKey)
                .withProperty("foodmate.runtime.service-jwt.java-kid", "java-test");
        assertDoesNotThrow(() -> new M03ConfigurationValidator(environment).validate());
    }

    private static MockEnvironment base(String profile) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        return environment.withProperty("foodmate.security.cookie-secure", "true")
                .withProperty("foodmate.runtime.service-jwt.enabled", "true");
    }
}
