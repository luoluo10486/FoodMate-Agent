package com.foodmate.application.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.runtime.persistence.ProtocolAuditStore;
import com.foodmate.gateway.MqConsumeDecision;
import com.foodmate.gateway.MqMessageHandler;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.runtime.V1RunEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;

/**
 * 消费 Python -> Java 的 RunEvent 消息（ADR-0005 §Outbox 与 Inbox）。
 *
 * <p>处理顺序固定为：解析 -> {@link V1RuntimeEventService#accept} 的 PostgreSQL 事务 （Inbox + 状态机 + 审计 + SSE
 * Outbox）-> 提交 -> 返回 ACK。事务提交后、ACK 前进程崩溃时 Broker 会重投，由 Inbox 的 {@code
 * uk_runtime_event_v2_idempotency} 幂等吸收。
 *
 * <p>失败分类（实施方案 §5.16）：
 *
 * <ul>
 *   <li>schema、digest、权限、fencing 这类确定性错误直接 REJECT，重试不可能成功；
 *   <li>{@code RUNTIME_EVENT_GAP} 是 RETRY——前序事件可能仍在投递中；
 *   <li>{@code RUNTIME_EVENT_OUT_OF_ORDER} 是 ACK——该序号已处理过，属于重投；
 *   <li>其余异常（数据库不可用等）默认 RETRY。
 * </ul>
 */
@Service
public class RuntimeEventMessageProcessor implements MqMessageHandler {
    private final V1RuntimeEventService events;
    private final ProtocolAuditStore auditStore;
    private final IdGenerator ids;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public RuntimeEventMessageProcessor(
            V1RuntimeEventService events, ProtocolAuditStore auditStore, IdGenerator ids) {
        this.events = events;
        this.auditStore = auditStore;
        this.ids = ids;
    }

    @Override
    public MqConsumeDecision handle(String body, MqMessageContext context) {
        V1RunEvent event;
        try {
            event = mapper.readValue(body, V1RunEvent.class);
        } catch (Exception exception) {
            // 还没拿到可信 run_id，属于 PreRunProtocolError：记录审计后丢弃，不重试。
            recordProtocolError(context, body, "RUNTIME_CONTRACT_INVALID");
            return MqConsumeDecision.REJECT;
        }
        try {
            events.accept(event);
            return MqConsumeDecision.ACK;
        } catch (com.foodmate.shared.runtime.RuntimeException exception) {
            return switch (exception.code()) {
                // 缺少前序事件：等待重投，顺序消费会挂起该队列直到补齐或进入 DLQ。
                case "RUNTIME_EVENT_GAP" -> MqConsumeDecision.RETRY;
                // 该序号已推进过，是 Broker 重投；accept 内部已登记拒绝原因。
                case "RUNTIME_EVENT_OUT_OF_ORDER" -> MqConsumeDecision.ACK;
                // digest 冲突、dispatch 已失效、事件类型不受支持：确定性错误。
                case "RUNTIME_EVENT_IDEMPOTENCY_CONFLICT",
                        "RUNTIME_STATE_CONFLICT",
                        "RUNTIME_CONTRACT_INVALID" ->
                        MqConsumeDecision.REJECT;
                default -> MqConsumeDecision.RETRY;
            };
        } catch (java.lang.RuntimeException exception) {
            // 数据库不可用、死锁等：事务已回滚，交给 Broker 重投。
            return MqConsumeDecision.RETRY;
        }
    }

    private void recordProtocolError(MqMessageContext context, String body, String errorCode) {
        try {
            // request_id 用消息 ID：同一条消息重投时按 (request_id, fingerprint) 幂等。
            auditStore.insert(
                    ids.nextId(),
                    context.messageId(),
                    digest(body),
                    errorCode,
                    mapper.writeValueAsString(
                            java.util.Map.of(
                                    "topic", context.topic(),
                                    "message_key",
                                            context.messageKey() == null
                                                    ? ""
                                                    : context.messageKey(),
                                    "reconsume_times", context.reconsumeTimes())));
        } catch (Exception ignored) {
            // 审计写入失败不能改变消费结论：消息本身已确定无法处理。
        }
    }

    private String digest(String value) {
        try {
            byte[] hash =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder("sha256:");
            for (byte item : hash) result.append(String.format("%02x", item));
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
