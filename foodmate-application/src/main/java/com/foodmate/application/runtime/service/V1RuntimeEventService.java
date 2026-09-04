package com.foodmate.application.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodmate.shared.runtime.V1RunEvent;
import java.util.List;

/** 校验、持久化并提供已确认的 V1 Runtime 事件。 */
public interface V1RuntimeEventService {
    EventResult accept(V1RunEvent event);

    /** 兼容既有饮食记录审批的终态事件入口。 */
    EventResult completeAgentWrite(
            long runId,
            long approvalRequestId,
            Long resourceId,
            String requestId,
            String traceId,
            boolean written);

    /** 为不同业务写入生成带资源摘要的唯一终态事件。 */
    EventResult completeAgentWrite(
            long runId,
            long approvalRequestId,
            Long resourceId,
            String requestId,
            String traceId,
            String resourceType,
            String operation,
            Long secondaryResourceId,
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
