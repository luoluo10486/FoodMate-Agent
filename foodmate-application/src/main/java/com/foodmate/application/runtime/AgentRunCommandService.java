package com.foodmate.application.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.runtime.V1RunCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Creates the durable AgentRun and immutable dispatch payload before any network call. */
@Service
public class AgentRunCommandService {
    private final JdbcTemplate jdbc;
    private final IdGenerator ids;
    private final UserAccountService accounts;
    private final ObjectMapper mapper;

    public AgentRunCommandService(ObjectProvider<JdbcTemplate> jdbcProvider,
                                  IdGenerator ids,
                                  UserAccountService accounts) {
        this.jdbc = jdbcProvider.getIfAvailable();
        this.ids = ids;
        this.accounts = accounts;
        this.mapper = new ObjectMapper().findAndRegisterModules()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Transactional
    public UserAccountService.MessageRecord createUserMessageRun(long userId, long sessionId, String content, String traceId) {
        long runId = ids.nextId();
        String runIdText = Long.toString(runId);
        if (jdbc == null) {
            return accounts.addMessage(userId, sessionId, "user", content, null, runId);
        }

        // The existing message FK points to agent_runs, so create the parent first and bind the message afterward.
        accounts.listMessages(userId, sessionId, 1, 1);
        jdbc.update("INSERT INTO agent_runs(agent_run_id,session_id,status,trace_id,created_by) VALUES (?,?,?, ?,?)",
                runId, sessionId, "queued", traceId, userId);
        UserAccountService.MessageRecord message = accounts.addMessage(userId, sessionId, "user", content, null, runId);

        jdbc.update("UPDATE agent_runs SET user_message_id=?,updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=?",
                message.messageId(), runId);

        String dispatchId = "dsp_" + UUID.randomUUID().toString().replace("-", "");
        String requestId = "req_" + UUID.randomUUID().toString().replace("-", "");
        Instant deadline = Instant.now().plusSeconds(60);
        Map<String, Object> authorizedContext = new LinkedHashMap<>();
        authorizedContext.put("session_id", Long.toString(sessionId));
        authorizedContext.put("timezone", "Asia/Shanghai");
        authorizedContext.put("locale", "zh-CN");
        authorizedContext.put("tool_contract_version", "v1");
        Map<String, Object> runtimeOptions = new LinkedHashMap<>();
        runtimeOptions.put("prompt_set_version", "foodmate-m1-3-stub");
        runtimeOptions.put("max_steps", 4);
        runtimeOptions.put("stream_answer", true);
        V1RunCommand.V1Message commandMessage = new V1RunCommand.V1Message(Long.toString(message.messageId()), content, List.of());
        String requestHash = digest(Map.of(
                "schema_version", "v1",
                "run_id", runIdText,
                "dispatch_id", dispatchId,
                "attempt", 1,
                "deadline_at", deadline,
                "message", commandMessage,
                "authorized_context", authorizedContext,
                "runtime_options", runtimeOptions));
        V1RunCommand command = new V1RunCommand("v1", runIdText, dispatchId, 1, requestId, traceId,
                requestHash, deadline, commandMessage, authorizedContext, runtimeOptions);
        String payload = json(command);
        long dispatchRowId = ids.nextId();
        String fence = "fence_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO agent_run_dispatches(agent_run_dispatch_id,agent_run_id,dispatch_id,attempt,active_epoch,fencing_token,admission_epoch,deadline_at) VALUES (?,?,?,?,?,?,?,?)",
                dispatchRowId, runId, dispatchId, 1, 1, fence, 0, java.sql.Timestamp.from(deadline));
        jdbc.update("INSERT INTO runtime_dispatch_outbox(outbox_id,agent_run_dispatch_id,agent_run_id,dispatch_id,run_id,attempt,schema_version,deadline_at,fencing_epoch,payload_json,request_hash) VALUES (?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?)",
                ids.nextId(), dispatchRowId, runId, dispatchId, runIdText, 1, "v1", java.sql.Timestamp.from(deadline), 1, payload, requestHash);
        jdbc.update("UPDATE agent_runs SET active_dispatch_id=?,updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=?", dispatchRowId, runId);
        return message;
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("runtime command is not JSON", exception); }
    }

    private String digest(Object value) {
        try {
            byte[] bytes = json(value).getBytes(StandardCharsets.UTF_8);
            return "sha256:" + HexFormatHolder.encode(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static final class HexFormatHolder {
        private static String encode(byte[] bytes) {
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) result.append(String.format("%02x", value));
            return result.toString();
        }
    }
}
