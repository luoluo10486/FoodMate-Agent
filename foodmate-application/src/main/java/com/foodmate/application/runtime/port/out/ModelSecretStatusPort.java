package com.foodmate.application.runtime.port.out;

/** Reads only non-reversible provider secret status; secret material never crosses this port. */
public interface ModelSecretStatusPort {
    SecretStatus status(String providerCode);

    record SecretStatus(boolean configured, String fingerprint) {}
}
