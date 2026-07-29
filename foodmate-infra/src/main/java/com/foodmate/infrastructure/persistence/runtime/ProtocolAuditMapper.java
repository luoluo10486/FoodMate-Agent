package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.persistence.ProtocolAuditStore;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProtocolAuditMapper extends ProtocolAuditStore {
    @Insert(
            "INSERT INTO protocol_error_audits(protocol_error_audit_id,request_id,fingerprint,error_code,raw_envelope_json) VALUES (#{id},#{requestId},#{fingerprint},#{errorCode},CAST(#{envelopeJson} AS jsonb)) ON CONFLICT (request_id,fingerprint) DO NOTHING")
    void insert(
            long id, String requestId, String fingerprint, String errorCode, String envelopeJson);
}
