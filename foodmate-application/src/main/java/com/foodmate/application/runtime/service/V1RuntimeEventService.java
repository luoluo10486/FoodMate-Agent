package com.foodmate.application.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodmate.shared.runtime.V1RunEvent;
import java.util.List;

/** 校验、持久化并提供已确认的 V1 Runtime 事件。 */
public interface V1RuntimeEventService {
    EventResult accept(V1RunEvent event);

    /** 在 Agent 写入审批完成后追加唯一终态事件。 */
    EventResult completeAgentWrite(
            long runId,
            long approvalRequestId,
            Long resourceId,
            String requestId,
            String traceId,
            boolean written);

    List<V1RunEvent> events(String runId);

    boolean exists(String runId);

    List<SseRecord> sseEvents(String runId, long afterSequence);

    long cursorFor(String runId, String cursor);

    String status(String runId);

    void requireRunOwner(String runId, long userId);

    record EventResult(String runId, String eventId, boolean duplicate, String status) {}

    record SseRecord(
            long streamSeq,
            String sseEventId,
            String eventType,
            JsonNode payload,
            boolean terminal) {}
}
