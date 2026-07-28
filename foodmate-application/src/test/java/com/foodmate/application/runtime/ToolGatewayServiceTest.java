package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.foodmate.shared.id.IdGenerator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;

class ToolGatewayServiceTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final ToolGatewayService gateway = new ToolGatewayService(provider(jdbc), (IdGenerator) () -> 99L);

    @Test
    void rejectsWriteSqlBeforeDatabaseExecution() {
        var result = gateway.execute(proposal("UPDATE agent_runs SET status='completed'"));
        assertEquals("rejected", result.status());
        assertEquals("SQL_PROPOSAL_NOT_READ_ONLY", result.errorCode());
        verifyNoInteractions(jdbc);
    }

    @Test
    void rejectsMalformedRunId() {
        var proposal = proposal("SELECT 1");
        proposal.put("run_id", "not-a-number");
        var result = gateway.execute(proposal);
        assertEquals("RUN_ID_INVALID", result.errorCode());
        verify(jdbc, never()).queryForList(any(String.class));
    }

    @Test
    void executesReadAndAuditsIt() {
        doReturn(true).when(jdbc).query(any(String.class), ArgumentMatchers.<ResultSetExtractor<Boolean>>any(), eq(42L));
        when(jdbc.queryForList("SELECT 1")).thenReturn(List.of(Map.of("value", 1)));
        var result = gateway.execute(proposal("SELECT 1"));
        assertEquals("succeeded", result.status());
        assertEquals(1, result.rows().size());
        verify(jdbc).update(startsWith("INSERT INTO sql_query_audits"), any(), eq(42L), eq("SELECT 1"), eq(1), anyLong(), any());
    }

    private static Map<String, Object> proposal(String sql) {
        return new java.util.HashMap<>(Map.of(
                "proposal_id", "proposal-1", "run_id", "42", "proposal_type", "sql_read",
                "payload", Map.of("statement", sql)));
    }

    private static ObjectProvider<JdbcTemplate> provider(JdbcTemplate value) {
        return new ObjectProvider<>() {
            public JdbcTemplate getObject(Object... args) { return value; }
            public JdbcTemplate getIfAvailable() { return value; }
            public JdbcTemplate getIfUnique() { return value; }
            public Stream<JdbcTemplate> orderedStream() { return Stream.of(value); }
            public Stream<JdbcTemplate> stream() { return Stream.of(value); }
        };
    }
}
