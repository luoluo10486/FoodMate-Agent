package com.foodmate.application.runtime.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.runtime.port.out.DeadLetterRepository;
import com.foodmate.application.runtime.service.RuntimeDlqService;
import com.foodmate.gateway.MqConsumeDecision;
import com.foodmate.shared.id.IdGenerator;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DLQ 登记与对账（ADR-0005 §DLQ 与对账，实施方案 §5.16）。
 *
 * <p>核心约束：<b>进入 DLQ 不等于 AgentRun 失败</b>。DLQ 只说明「这条消息耗尽了重试」， 可能是重复投递、可能 Run 早已完成、也可能确实卡住了。因此本类分两步：
 *
 * <ol>
 *   <li>{@link #handle} 只负责把消息原样登记到 {@code runtime_message_dlq}，永远 ACK—— 如果连 DLQ 归档都重试，消息会在 DLQ
 *       Topic 里再次堆积。
 *   <li>{@link #reconcile} 定时对账 Run、dispatch 与事件 Inbox 后才裁决， 并且只写对账结论，不直接把 Run 改成 failed。
 * </ol>
 */
@Service
public class RuntimeDlqServiceImpl implements RuntimeDlqService {
    private final DeadLetterRepository store;
    private final IdGenerator ids;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final String consumerGroup;

    public RuntimeDlqServiceImpl(
            DeadLetterRepository store,
            IdGenerator ids,
            org.springframework.core.env.Environment environment) {
        this.store = store;
        this.ids = ids;
        this.consumerGroup =
                environment.getProperty(
                        "foodmate.runtime.rocketmq.java-event-consumer-group",
                        "foodmate-java-agent-event-v1");
    }

    @Override
    public MqConsumeDecision handle(String body, MqMessageContext context) {
        Map<String, String> properties = context.properties();
        try {
            store.insert(
                    new DeadLetterRepository.DlqMessage(
                            ids.nextId(),
                            consumerGroup,
                            context.topic(),
                            context.messageId(),
                            context.messageKey(),
                            properties.get("foodmate_run_id"),
                            properties.get("foodmate_dispatch_id"),
                            parseInt(properties.get("foodmate_attempt")),
                            properties.get("foodmate_event_id"),
                            parseLong(properties.get("foodmate_event_seq")),
                            properties.get("foodmate_request_hash"),
                            context.reconsumeTimes(),
                            "RUNTIME_MESSAGE_DEAD_LETTERED",
                            properties.get("foodmate_last_error"),
                            envelope(body)));
        } catch (Exception exception) {
            // 归档失败也不重投：DLQ 消息重投只会让同一条消息反复占用消费位。
            // 消息仍留在 Broker 的 DLQ Topic 中，可由人工 mqadmin 排查。
            return MqConsumeDecision.ACK;
        }
        return MqConsumeDecision.ACK;
    }

    /**
     * 对账待处理 DLQ 记录。只裁决消息本身的去向，不改写 AgentRun 状态。
     *
     * <p>三种确定性结论：
     *
     * <ul>
     *   <li>事件已在 Inbox 中 -> {@code resolved_duplicate}，说明业务事务其实成功过；
     *   <li>Run 已进入终态 -> {@code resolved_terminal}，迟到消息不再有业务意义；
     *   <li>其余 -> {@code needs_attention}，交给人工或后续重放，不自动判失败。
     * </ul>
     */
    @Scheduled(fixedDelayString = "${foodmate.runtime.dlq-reconcile-ms:30000}")
    @Transactional
    @Override
    public void reconcile() {
        List<DeadLetterRepository.DlqEntry> rows = store.findPending(50);
        for (DeadLetterRepository.DlqEntry row : rows) {
            Long runId = parseRunId(row.runId());
            if (runId == null) {
                resolve(row.id(), "needs_attention", "消息缺少可解析的 run_id，无法对账");
                continue;
            }
            if (row.eventId() != null
                    && !row.eventId().isBlank()
                    && store.inboxCount(runId, row.eventId()) > 0) {
                resolve(row.id(), "resolved_duplicate", "事件已在 Inbox 中，业务事务已提交");
                continue;
            }
            List<String> status = store.findRunStatuses(runId);
            if (status.isEmpty()) {
                resolve(row.id(), "needs_attention", "run 不存在，可能是过期或非法消息");
            } else if (List.of("completed", "failed", "cancelled", "superseded")
                    .contains(status.getFirst())) {
                resolve(
                        row.id(),
                        "resolved_terminal",
                        "run 已处于终态 " + status.getFirst() + "，迟到消息无需应用");
            } else {
                resolve(
                        row.id(),
                        "needs_attention",
                        "run 仍处于 " + status.getFirst() + "，需要人工确认是否重放");
            }
        }
    }

    private void resolve(long dlqId, String state, String note) {
        store.resolve(dlqId, state, note);
    }

    private String envelope(String body) {
        try {
            // 原文可能不是合法 JSON（正是它进 DLQ 的原因），包一层保证列类型合法。
            mapper.readTree(body);
            return body;
        } catch (Exception exception) {
            try {
                return mapper.writeValueAsString(Map.of("raw", body));
            } catch (Exception nested) {
                return "{}";
            }
        }
    }

    private Integer parseInt(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long parseLong(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long parseRunId(String value) {
        try {
            return value == null ? null : Long.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
