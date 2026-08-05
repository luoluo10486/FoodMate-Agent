package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.foodmate.application.runtime.messaging.MessageProperties;
import com.foodmate.application.runtime.messaging.MqConsumeDecision;
import com.foodmate.application.runtime.messaging.MqMessageHandler;
import com.foodmate.application.runtime.port.out.ProtocolAuditRepository;
import com.foodmate.application.runtime.port.out.RuntimeEventRepository;
import com.foodmate.application.runtime.processor.RuntimeEventMessageProcessor;
import com.foodmate.application.runtime.service.V1RuntimeEventService;
import com.foodmate.application.runtime.service.impl.V1RuntimeEventServiceImpl;
import com.foodmate.shared.runtime.V1RunEvent;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

/**
 * MQ 事件消费的失败分类（实施方案 §5.16）。
 *
 * <p>分类错误的后果是具体的：把 GAP 当成 REJECT 会永久丢失事件； 把 digest 冲突当成 RETRY 会让同一条坏消息一直占用消费位直到进 DLQ。
 */
class RuntimeEventMessageProcessorTest {

    private static final String EVENT_JSON =
            """
            {"schema_version":"v1","run_id":"1001","dispatch_id":"dsp_1","attempt":1,
             "event_id":"evt_1","event_seq":1,"request_id":"req_1","trace_id":"trace_1",
             "request_hash":"sha256:abc","occurred_at":"2026-07-26T10:00:00Z",
             "event_type":"run.accepted","payload":{"status":"queued"}}
            """;

    private static final MqMessageHandler.MqMessageContext CONTEXT =
            new MqMessageHandler.MqMessageContext(
                    "foodmate-agent-event-v1", "MSG1", "1001", 0, MessageProperties.empty());

    /** 让 accept() 抛出指定错误码的事件服务替身。 */
    private static RuntimeEventMessageProcessor processorFailingWith(String code) {
        V1RuntimeEventService events =
                new V1RuntimeEventServiceImpl(nullProvider(), () -> 1L) {
                    @Override
                    public synchronized EventResult accept(V1RunEvent event) {
                        if (code == null)
                            return new EventResult(event.runId(), event.eventId(), false, "queued");
                        throw new com.foodmate.shared.runtime.RuntimeException(code, "test");
                    }
                };
        return new RuntimeEventMessageProcessor(
                events, mock(ProtocolAuditRepository.class), () -> 1L);
    }

    @Test
    void acceptedEventIsAcknowledgedAfterTransactionCommits() {
        assertEquals(MqConsumeDecision.ACK, processorFailingWith(null).handle(EVENT_JSON, CONTEXT));
    }

    @Test
    void missingPredecessorRetriesInsteadOfDroppingTheEvent() {
        // event_seq 有缺口：前序事件可能仍在投递中，必须等待重投而不是丢弃。
        assertEquals(
                MqConsumeDecision.RETRY,
                processorFailingWith("RUNTIME_EVENT_GAP").handle(EVENT_JSON, CONTEXT));
    }

    @Test
    void alreadyProcessedSequenceIsAcknowledgedAsRedelivery() {
        // seq 小于期望值说明该序号已推进过，重试没有意义，重投也不该反复失败。
        assertEquals(
                MqConsumeDecision.ACK,
                processorFailingWith("RUNTIME_EVENT_OUT_OF_ORDER").handle(EVENT_JSON, CONTEXT));
    }

    @Test
    void deterministicErrorsAreRejectedWithoutRetry() {
        assertEquals(
                MqConsumeDecision.REJECT,
                processorFailingWith("RUNTIME_EVENT_IDEMPOTENCY_CONFLICT")
                        .handle(EVENT_JSON, CONTEXT));
        assertEquals(
                MqConsumeDecision.REJECT,
                processorFailingWith("RUNTIME_STATE_CONFLICT").handle(EVENT_JSON, CONTEXT));
        assertEquals(
                MqConsumeDecision.REJECT,
                processorFailingWith("RUNTIME_CONTRACT_INVALID").handle(EVENT_JSON, CONTEXT));
    }

    @Test
    void infrastructureFailuresRetry() {
        V1RuntimeEventService events =
                new V1RuntimeEventServiceImpl(nullProvider(), () -> 1L) {
                    @Override
                    public synchronized EventResult accept(V1RunEvent event) {
                        throw new IllegalStateException("connection pool exhausted");
                    }
                };
        assertEquals(
                MqConsumeDecision.RETRY,
                new RuntimeEventMessageProcessor(
                                events, mock(ProtocolAuditRepository.class), () -> 1L)
                        .handle(EVENT_JSON, CONTEXT));
    }

    @Test
    void unparsableBodyIsRejectedBecauseNoTrustedRunIdExists() {
        // 还没有可信 run_id，属于 PreRunProtocolError：不能附着到某个 Run，也不该重试。
        assertEquals(
                MqConsumeDecision.REJECT, processorFailingWith(null).handle("{not json", CONTEXT));
    }

    private static ObjectProvider<RuntimeEventRepository> nullProvider() {
        return new ObjectProvider<>() {
            public RuntimeEventRepository getObject(Object... args) {
                return null;
            }

            public RuntimeEventRepository getIfAvailable() {
                return null;
            }

            public RuntimeEventRepository getIfUnique() {
                return null;
            }

            public Stream<RuntimeEventRepository> orderedStream() {
                return Stream.empty();
            }

            public Stream<RuntimeEventRepository> stream() {
                return Stream.empty();
            }
        };
    }
}
