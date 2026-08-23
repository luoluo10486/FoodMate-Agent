package com.foodmate.application.runtime.service.impl;

import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.runtime.port.out.AgentFeedbackRepository;
import com.foodmate.application.runtime.port.out.AgentFeedbackRepository.FeedbackTarget;
import com.foodmate.application.runtime.port.out.AgentFeedbackRepository.FeedbackView;
import com.foodmate.application.runtime.service.AgentFeedbackService;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在同一事务中保存结构化反馈和统一业务审计；不保存回答正文或 Prompt。 */
@Service
public class AgentFeedbackServiceImpl implements AgentFeedbackService {
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final Set<String> ALLOWED_REASONS =
            Set.of(
                    "incorrect",
                    "incomplete",
                    "irrelevant",
                    "too_verbose",
                    "missing_citation",
                    "other",
                    "fabricated_execution",
                    "unsafe_or_privacy");
    private static final Set<String> HIGH_RISK_REASONS =
            Set.of("fabricated_execution", "unsafe_or_privacy");

    private final AgentFeedbackRepository store;
    private final IdGenerator ids;
    private final OperationAuditService audit;
    private final boolean enabled;
    private final int commentMaxLength;
    private final boolean highRiskAuditEnabled;

    public AgentFeedbackServiceImpl(
            ObjectProvider<AgentFeedbackRepository> storeProvider,
            IdGenerator ids,
            ObjectProvider<OperationAuditService> auditProvider,
            @Value("${foodmate.agent.feedback.enabled:true}") boolean enabled,
            @Value("${foodmate.agent.feedback.comment-max-length:1000}") int commentMaxLength,
            @Value("${foodmate.agent.feedback.audit-high-risk-enabled:true}")
                    boolean highRiskAuditEnabled) {
        this.store = storeProvider.getIfAvailable();
        this.ids = ids;
        this.audit = auditProvider == null ? null : auditProvider.getIfAvailable();
        this.enabled = enabled;
        this.commentMaxLength = commentMaxLength;
        this.highRiskAuditEnabled = highRiskAuditEnabled;
    }

    @Transactional
    @Override
    public FeedbackView submit(long userId, long runId, long messageId, SubmitCommand command) {
        if (!enabled) throw new BusinessException(ErrorCode.AGENT_FEEDBACK_DISABLED);
        if (store == null || ids == null)
            throw new BusinessException(ErrorCode.COORDINATION_UNAVAILABLE, "反馈存储暂不可用");
        ValidatedCommand validated = validate(command);
        FeedbackView previous = store.findByIdempotency(userId, validated.idempotencyKey());
        if (previous != null) {
            if (!previous.parametersDigest().equals(validated.parametersDigest()))
                throw new BusinessException(ErrorCode.AGENT_FEEDBACK_CONFLICT, "反馈幂等键参数不一致");
            return previous;
        }
        FeedbackTarget target = store.target(userId, runId, messageId);
        if (target == null)
            throw new BusinessException(ErrorCode.AGENT_FEEDBACK_NOT_FOUND, "可反馈的 Agent 回答不存在");
        FeedbackView existing = store.findByMessage(userId, runId, messageId);
        if (existing != null)
            throw new BusinessException(ErrorCode.AGENT_FEEDBACK_ALREADY_SUBMITTED, "该回答已经提交过反馈");

        long feedbackId = ids.nextId();
        int inserted =
                store.insert(
                        new AgentFeedbackRepository.FeedbackWrite(
                                feedbackId,
                                userId,
                                runId,
                                messageId,
                                validated.helpful(),
                                validated.reasonCodes(),
                                validated.comment(),
                                target.traceId(),
                                target.evalId(),
                                target.modelRouteVersion(),
                                target.promptVersion(),
                                target.rubricVersion(),
                                validated.highRisk(),
                                validated.idempotencyKey(),
                                validated.parametersDigest()));
        if (inserted != 1) {
            FeedbackView concurrent = store.findByMessage(userId, runId, messageId);
            if (concurrent != null) return concurrent;
            throw new BusinessException(ErrorCode.AGENT_FEEDBACK_CONFLICT, "反馈提交冲突");
        }
        if (audit != null) {
            audit.record(
                    userId,
                    "agent_feedback",
                    Long.toString(feedbackId),
                    "agent.feedback.submit",
                    "success",
                    null,
                    validated.parametersDigest(),
                    validated.idempotencyKey(),
                    Map.of(
                            "run_id", Long.toString(runId),
                            "message_id", Long.toString(messageId),
                            "helpful", Boolean.toString(validated.helpful()),
                            "reason_count", Integer.toString(validated.reasonCodes().size()),
                            "audit_priority",
                                    validated.highRisk() && highRiskAuditEnabled
                                            ? "high"
                                            : "normal"));
        }
        return new FeedbackView(
                feedbackId,
                userId,
                runId,
                messageId,
                validated.helpful(),
                validated.reasonCodes(),
                validated.highRisk(),
                validated.idempotencyKey(),
                validated.parametersDigest());
    }

    private ValidatedCommand validate(SubmitCommand command) {
        if (command == null || command.helpful() == null)
            throw new BusinessException(ErrorCode.AGENT_FEEDBACK_INVALID, "请选择反馈结果");
        String idempotencyKey = normalizeKey(command.idempotencyKey());
        List<String> reasons = command.reasonCodes() == null ? List.of() : command.reasonCodes();
        if (reasons.size() > 6)
            throw new BusinessException(ErrorCode.AGENT_FEEDBACK_INVALID, "反馈原因过多");
        LinkedHashSet<String> normalizedReasons = new LinkedHashSet<>();
        for (String reason : reasons) {
            String normalized = reason == null ? "" : reason.trim().toLowerCase(Locale.ROOT);
            if (!ALLOWED_REASONS.contains(normalized))
                throw new BusinessException(ErrorCode.AGENT_FEEDBACK_INVALID, "反馈原因无效");
            normalizedReasons.add(normalized);
        }
        if (!command.helpful() && normalizedReasons.isEmpty())
            throw new BusinessException(ErrorCode.AGENT_FEEDBACK_INVALID, "请选择至少一个原因");
        String comment = truncateComment(command.comment());
        boolean highRisk = normalizedReasons.stream().anyMatch(HIGH_RISK_REASONS::contains);
        String digest = digest(command.helpful(), List.copyOf(normalizedReasons), comment);
        return new ValidatedCommand(
                command.helpful(),
                List.copyOf(normalizedReasons),
                comment,
                highRisk,
                idempotencyKey,
                digest);
    }

    private String normalizeKey(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_IDEMPOTENCY_KEY_LENGTH)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "Idempotency-Key 无效");
        return value.trim();
    }

    private String truncateComment(String value) {
        if (commentMaxLength < 0 || commentMaxLength > 1000)
            throw new IllegalStateException("feedback comment max length is invalid");
        if (value == null || value.isBlank() || commentMaxLength == 0) return null;
        String trimmed = value.trim();
        int end = trimmed.length();
        if (trimmed.length() > commentMaxLength)
            end = trimmed.offsetByCodePoints(0, trimmed.codePointCount(0, commentMaxLength));
        return trimmed.substring(0, end);
    }

    private String digest(boolean helpful, List<String> reasons, String comment) {
        String canonical =
                helpful + "|" + String.join(",", reasons) + "|" + (comment == null ? "" : comment);
        try {
            return "sha256:"
                    + HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ValidatedCommand(
            boolean helpful,
            List<String> reasonCodes,
            String comment,
            boolean highRisk,
            String idempotencyKey,
            String parametersDigest) {}
}
