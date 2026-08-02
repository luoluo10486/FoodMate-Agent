package com.foodmate.application.runtime.port.out;

public interface ProtocolAuditRepository {
    void insert(
            long id, String requestId, String fingerprint, String errorCode, String envelopeJson);
}
