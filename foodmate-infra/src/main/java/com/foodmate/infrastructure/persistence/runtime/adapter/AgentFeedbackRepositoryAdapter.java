package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.runtime.port.out.AgentFeedbackRepository;
import com.foodmate.infrastructure.persistence.runtime.AgentFeedbackMapper;
import com.foodmate.infrastructure.persistence.runtime.AgentFeedbackMapper.RawFeedbackView;
import java.io.IOException;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 将反馈端口连接到 PostgreSQL；应用层不直接依赖 MyBatis 或表结构。 */
@Repository
@Profile("local")
public class AgentFeedbackRepositoryAdapter implements AgentFeedbackRepository {
    private final AgentFeedbackMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AgentFeedbackRepositoryAdapter(AgentFeedbackMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public FeedbackTarget target(long userId, long runId, long messageId) {
        return mapper.target(userId, runId, messageId);
    }

    @Override
    public FeedbackView findByIdempotency(long userId, String idempotencyKey) {
        return map(mapper.findByIdempotency(userId, idempotencyKey));
    }

    @Override
    public FeedbackView findByMessage(long userId, long runId, long messageId) {
        return map(mapper.findByMessage(userId, runId, messageId));
    }

    @Override
    public int insert(FeedbackWrite write) {
        return mapper.insert(
                new AgentFeedbackMapper.WriteParameters(
                        write.feedbackId(),
                        write.userId(),
                        write.runId(),
                        write.messageId(),
                        write.helpful(),
                        json(write.reasonCodes()),
                        write.comment(),
                        write.traceId(),
                        write.evalId(),
                        write.modelRouteVersion(),
                        write.promptVersion(),
                        write.rubricVersion(),
                        write.highRisk(),
                        write.idempotencyKey(),
                        write.parametersDigest()));
    }

    private FeedbackView map(RawFeedbackView value) {
        if (value == null) return null;
        return new FeedbackView(
                value.feedbackId(),
                value.userId(),
                value.runId(),
                value.messageId(),
                value.helpful(),
                parseReasons(value.reasonCodes()),
                value.highRisk(),
                value.idempotencyKey(),
                value.parametersDigest());
    }

    private String json(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "feedback reason codes cannot be serialized", exception);
        }
    }

    private List<String> parseReasons(String value) {
        if (value == null || value.isBlank()) return List.of();
        try {
            return objectMapper.readValue(
                    value,
                    objectMapper
                            .getTypeFactory()
                            .constructCollectionType(List.class, String.class));
        } catch (IOException exception) {
            throw new IllegalStateException("feedback reason codes are invalid", exception);
        }
    }
}
