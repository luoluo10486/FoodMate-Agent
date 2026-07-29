package com.foodmate.application.runtime.persistence;

public interface ProtocolAuditStore {
    void insert(
            long id, String requestId, String fingerprint, String errorCode, String envelopeJson);
}
