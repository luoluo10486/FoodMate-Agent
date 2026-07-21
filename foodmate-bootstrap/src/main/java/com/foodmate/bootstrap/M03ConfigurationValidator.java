package com.foodmate.bootstrap;

import jakarta.annotation.PostConstruct;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Locale;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/** Enforces the production and development configuration boundary without logging secrets. */
@Component
public final class M03ConfigurationValidator {
    private final Environment environment;

    public M03ConfigurationValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    void validate() {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            validateDatabase(true);
            requireBoolean("foodmate.security.cookie-secure", true, "FOODMATE_COOKIE_SECURE");
            validateRuntimeKeysIfEnabled();
        } else if (environment.acceptsProfiles(Profiles.of("dev"))) {
            validateDatabase(false);
        }
    }

    private void validateDatabase(boolean production) {
        String url = value("spring.datasource.url");
        String username = value("spring.datasource.username");
        String password = value("spring.datasource.password");
        if (url.isBlank() || username.isBlank() || password.isBlank()) {
            throw invalid("database configuration");
        }
        if (production) {
            String lowerUrl = url.toLowerCase(Locale.ROOT);
            if (lowerUrl.contains("localhost") || lowerUrl.contains("127.0.0.1") || lowerUrl.contains("socketfactory")) {
                throw invalid("database host");
            }
            if ("postgres".equalsIgnoreCase(username) && "postgres".equalsIgnoreCase(password)) {
                throw invalid("database default credentials");
            }
        }
    }

    private void validateRuntimeKeysIfEnabled() {
        if (!Boolean.parseBoolean(value("foodmate.runtime.service-jwt.enabled"))) return;
        requireNonBlank("foodmate.runtime.service-jwt.java-private-key");
        requireNonBlank("foodmate.runtime.service-jwt.java-public-key");
        requireNonBlank("foodmate.runtime.service-jwt.python-public-key");
        requireNonBlank("foodmate.runtime.service-jwt.java-kid");
        parsePrivateKey(value("foodmate.runtime.service-jwt.java-private-key"));
        parsePublicKey(value("foodmate.runtime.service-jwt.java-public-key"));
        parsePublicKey(value("foodmate.runtime.service-jwt.python-public-key"));
    }

    private void requireBoolean(String property, boolean expected, String label) {
        if (Boolean.parseBoolean(value(property)) != expected) throw invalid(label);
    }

    private void requireNonBlank(String property) {
        if (value(property).isBlank()) throw invalid(property);
    }

    private String value(String property) {
        String value = environment.getProperty(property);
        return value == null ? "" : value.trim();
    }

    private static void parsePrivateKey(String value) {
        try {
            KeyFactory.getInstance("Ed25519").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(value)));
        } catch (Exception exception) {
            throw invalid("runtime private key format");
        }
    }

    private static void parsePublicKey(String value) {
        try {
            KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(value)));
        } catch (Exception exception) {
            throw invalid("runtime public key format");
        }
    }

    private static IllegalStateException invalid(String category) {
        return new IllegalStateException("M0-3 configuration invalid: " + category);
    }
}
