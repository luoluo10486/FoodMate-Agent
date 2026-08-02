package com.foodmate.application.runtime.service.impl;

import com.foodmate.application.runtime.port.out.CancellationRepository;
import com.foodmate.application.runtime.service.RuntimeCancellationService;
import com.foodmate.application.runtime.service.V1RuntimeEventService;
import com.foodmate.gateway.V1RuntimeClient;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.runtime.V1CancelCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuntimeCancellationServiceImpl implements RuntimeCancellationService {
    private final CancellationRepository store;
    private final IdGenerator ids;
    private final V1RuntimeClient client;
    private final V1RuntimeEventService events;

    public RuntimeCancellationServiceImpl(
            CancellationRepository store,
            IdGenerator ids,
            ObjectProvider<V1RuntimeClient> clientProvider,
            V1RuntimeEventService events) {
        this.store = store;
        this.ids = ids;
        this.client = clientProvider.getIfAvailable();
        this.events = events;
    }

    @Transactional
    @Override
    public CancelResult request(long userId, String runId, String reason) {
        // 取消、状态检查和 cancellation_epoch 提升必须先完成用户归属校验。
        events.requireRunOwner(runId, userId);
        long numeric = parse(runId);
        CancellationRepository.ActiveDispatch active = store.findActiveDispatch(numeric);
        if (active == null)
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_STATE_CONFLICT", "active dispatch not found");
        String status = active.runStatus();
        if (status.equals("completed") || status.equals("failed") || status.equals("cancelled"))
            return new CancelResult(runId, status, true);
        String cancelId = "can_" + UUID.randomUUID().toString().replace("-", "");
        String dispatchId = active.dispatchId();
        int attempt = active.attempt();
        Instant requestedAt = Instant.now();
        Instant deadline = requestedAt.plusSeconds(30);
        String hash =
                digest(
                        runId
                                + "|"
                                + dispatchId
                                + "|"
                                + attempt
                                + "|"
                                + cancelId
                                + "|"
                                + reason
                                + "|"
                                + deadline);
        store.insertRequested(
                new CancellationRepository.NewCancellation(
                        ids.nextId(), numeric, cancelId, dispatchId, hash, reason, requestedAt));
        store.incrementCancellationEpoch(numeric);
        return new CancelResult(runId, "requested", false);
    }

    @Scheduled(fixedDelayString = "${foodmate.runtime.cancel-poll-ms:500}")
    @Override
    public void publishRequested() {
        if (client == null) return;
        var rows = store.findRequested(10);
        for (CancellationRepository.PendingCancellation pending : rows) {
            try {
                V1CancelCommand command =
                        new V1CancelCommand(
                                "v1",
                                pending.runId(),
                                pending.dispatchId(),
                                pending.attempt(),
                                pending.cancelId(),
                                "req_cancel_" + pending.cancelId(),
                                "trace_cancel_" + pending.runId(),
                                pending.requestHash(),
                                pending.requestedAt().plusSeconds(30),
                                pending.reason(),
                                pending.requestedAt());
                // 浏览器取消先落库，再由这里通过 command Topic 可靠发布（ADR-0005 §控制命令）。
                V1RuntimeClient.Response response = client.cancel(command);
                store.markDispatched(
                        pending.id(),
                        response.messageId() == null ? "http" : "rocketmq",
                        response.messageId());
            } catch (Exception exception) {
                // Runtime 暂时不可用时保留 requested，下一轮定时任务使用同一取消记录重试。
            }
        }
    }

    private long parse(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_STATE_CONFLICT", "run id is invalid");
        }
    }

    private String digest(String value) {
        try {
            return "sha256:"
                    + hex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String hex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte value : bytes) result.append(String.format("%02x", value));
        return result.toString();
    }
}
