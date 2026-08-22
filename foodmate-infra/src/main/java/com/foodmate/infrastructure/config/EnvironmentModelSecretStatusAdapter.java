package com.foodmate.infrastructure.config;

import com.foodmate.application.runtime.port.out.ModelSecretStatusPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Exposes only configured/fingerprint state for a provider API key from process configuration. */
@Component
@Profile("local")
public class EnvironmentModelSecretStatusAdapter implements ModelSecretStatusPort {
    private final Environment environment;

    public EnvironmentModelSecretStatusAdapter(Environment environment) {
        this.environment = environment;
    }

    @Override
    public SecretStatus status(String providerCode) {
        String key =
                "FOODMATE_MODEL_PROVIDER_"
                        + providerCode.toUpperCase().replace('-', '_')
                        + "_API_KEY";
        String secret = environment.getProperty(key, "");
        if (secret == null || secret.isBlank()) return new SecretStatus(false, null);
        return new SecretStatus(true, fingerprint(secret));
    }

    private static String fingerprint(String value) {
        try {
            return "sha256:"
                    + HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
