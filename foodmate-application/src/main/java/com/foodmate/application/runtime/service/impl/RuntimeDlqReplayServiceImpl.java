package com.foodmate.application.runtime.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.common.port.out.OperationAuditPort.IdempotencyRecord;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.runtime.port.out.DeadLetterRepository;
import com.foodmate.application.runtime.port.out.DeadLetterRepository.ReplayCandidate;
import com.foodmate.application.runtime.service.RuntimeDlqReplayService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** DLQ 重放的权威入口；只创建 replay outbox，不在 HTTP 请求中直接访问 Broker。 */
@Service
public class RuntimeDlqReplayServiceImpl implements RuntimeDlqReplayService {
    private static final String ACTION = "runtime.dlq.replay";
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final DeadLetterRepository store;
    private final OperationAuditService audit;
    private final IdGenerator ids;
    private final ObjectMapper mapper;
    private final String eventTopic;
    private final String eventConsumerGroup;

    /** Compatibility constructor for application-only tests. */
    public RuntimeDlqReplayServiceImpl(
            DeadLetterRepository store, OperationAuditService audit, IdGenerator ids) {
        this(store, audit, ids, "foodmate-agent-event-v1", "foodmate-java-agent-event-v1");
    }

    @Autowired
    public RuntimeDlqReplayServiceImpl(
            DeadLetterRepository store,
            OperationAuditService audit,
            IdGenerator ids,
            @Value("${foodmate.runtime.rocketmq.event-topic:foodmate-agent-event-v1}")
                    String eventTopic,
            @Value(
                            "${foodmate.runtime.rocketmq.java-event-consumer-group:foodmate-java-agent-event-v1}")
                    String eventConsumerGroup) {
        this.store = Objects.requireNonNull(store);
        this.audit = Objects.requireNonNull(audit);
        this.ids = Objects.requireNonNull(ids);
        this.mapper = new ObjectMapper().findAndRegisterModules();
        this.eventTopic = requireTopic(eventTopic);
        this.eventConsumerGroup = requireTopic(eventConsumerGroup);
    }

    @Override
    @Transactional
    public ReplayResult request(long dlqId, Command command) {
        String targetId = Long.toString(dlqId);
        String digest = digest(dlqId);
        boolean reserved = false;
        try {
            validate(command, dlqId);
            requireSuperadmin(command);
            IdempotencyRecord previous =
                    audit.findIdempotency(command.operatorId(), command.idempotencyKey());
            if (previous != null) return replay(previous, digest, dlqId);

            ReplayCandidate candidate = store.findReplayCandidate(dlqId);
            validateCandidate(candidate);
            if (store.findActiveReplay(dlqId) != null)
                throw new BusinessException(ErrorCode.DLQ_REPLAY_ACTIVE);

            if (audit.reserve(
                            command.operatorId(),
                            "runtime_message_dlq",
                            targetId,
                            ACTION,
                            digest,
                            command.idempotencyKey(),
                            Map.of("reason", "manual_replay", "source", "admin"))
                    != 1) {
                previous = audit.findIdempotency(command.operatorId(), command.idempotencyKey());
                if (previous != null) return replay(previous, digest, dlqId);
                throw new BusinessException(ErrorCode.CONFLICT, "幂等请求无法占用");
            }
            reserved = true;

            long replayId = ids.nextId();
            if (store.insertReplay(
                            new DeadLetterRepository.ReplayRequest(
                                    replayId,
                                    dlqId,
                                    command.operatorId(),
                                    command.idempotencyKey(),
                                    candidate))
                    != 1) {
                throw new BusinessException(ErrorCode.DLQ_REPLAY_ACTIVE);
            }
            ReplayResult result =
                    new ReplayResult(replayId, dlqId, "queued", candidate.originalMessageId());
            audit.complete(command.operatorId(), command.idempotencyKey(), json(result));
            return result;
        } catch (RuntimeException exception) {
            if (reserved) {
                recordFailureAfterRollback(command, targetId, digest, errorCode(exception));
            }
            throw exception;
        }
    }

    private ReplayResult replay(IdempotencyRecord previous, String digest, long dlqId) {
        if (!digest.equals(previous.parametersDigest()))
            throw new BusinessException(ErrorCode.CONFLICT, "幂等键对应的重放参数已变化");
        if (!"success".equalsIgnoreCase(previous.result()))
            throw new BusinessException(ErrorCode.CONFLICT, "幂等请求正在处理中或已失败");
        try {
            var node = mapper.readTree(previous.responseJson());
            return new ReplayResult(
                    node.path("replayId").asLong(),
                    node.path("dlqId").asLong(dlqId),
                    node.path("status").asText("queued"),
                    node.path("originalMessageId").asText(""));
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "重放审计结果无效");
        }
    }

    private void validateCandidate(ReplayCandidate candidate) {
        if (candidate == null) throw new BusinessException(ErrorCode.DLQ_REPLAY_NOT_ELIGIBLE);
        if (!eventTopic.equals(candidate.sourceTopic())
                || !eventConsumerGroup.equals(candidate.consumerGroup())
                || blank(candidate.originalMessageId())
                || blank(candidate.requestHash())
                || blank(candidate.payload())
                || blank(candidate.eventId())
                || blank(candidate.runId())
                || blank(candidate.dispatchId()))
            throw new BusinessException(ErrorCode.DLQ_REPLAY_FACT_INCOMPLETE);
        try {
            mapper.readTree(candidate.payload());
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.DLQ_REPLAY_FACT_INCOMPLETE);
        }
    }

    private static void validate(Command command, long dlqId) {
        if (dlqId <= 0 || command == null || command.operatorId() <= 0)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "重放请求参数无效");
        if (command.idempotencyKey() == null
                || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > MAX_IDEMPOTENCY_KEY_LENGTH)
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT, "Idempotency-Key 必须为 1-128 个字符");
        if (!command.confirmed()
                || command.confirmationDigest() == null
                || !MessageDigest.isEqual(
                        RuntimeDlqReplayService.confirmationDigest(dlqId)
                                .getBytes(StandardCharsets.UTF_8),
                        command.confirmationDigest().getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException(ErrorCode.CONFLICT, "需要匹配的重放确认摘要");
    }

    private static void requireSuperadmin(Command command) {
        if (command.operatorRole() != UserRole.SUPERADMIN)
            throw new BusinessException(ErrorCode.FORBIDDEN, "DLQ 重放仅允许 superadmin 操作");
    }

    private static String digest(long dlqId) {
        try {
            return "sha256:"
                    + HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    (ACTION + "|" + dlqId)
                                                            .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String json(ReplayResult result) {
        try {
            return mapper.writeValueAsString(result);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize DLQ replay result", exception);
        }
    }

    private static String errorCode(RuntimeException exception) {
        return exception instanceof BusinessException businessException
                ? businessException.errorCode().code()
                : ErrorCode.INTERNAL_ERROR.code();
    }

    private void recordFailureAfterRollback(
            Command command, String targetId, String digest, String errorCode) {
        Runnable record =
                () ->
                        audit.recordFailure(
                                command.operatorId(),
                                "runtime_message_dlq",
                                targetId,
                                ACTION,
                                "failed",
                                errorCode,
                                digest,
                                command.idempotencyKey(),
                                Map.of("failure", "dlq_replay"));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            record.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) record.run();
                    }
                });
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String requireTopic(String value) {
        if (blank(value) || !value.matches("[a-zA-Z0-9_-]+"))
            throw new IllegalArgumentException("event topic is invalid");
        return value;
    }
}
