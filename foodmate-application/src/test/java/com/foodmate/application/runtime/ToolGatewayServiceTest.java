package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.foodmate.application.runtime.persistence.ToolGatewayStore;
import com.foodmate.shared.id.IdGenerator;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ToolGatewayServiceTest {
    private final ToolGatewayStore store = mock(ToolGatewayStore.class);
    private final ToolGatewayService gateway =
            new ToolGatewayService(store, (IdGenerator) () -> 99L);

    @Test
    void rejectsWriteSqlBeforeDatabaseExecution() {
        var result = gateway.execute(proposal("UPDATE agent_runs SET status='completed'"));
        assertEquals("rejected", result.status());
        assertEquals("SQL_PROPOSAL_NOT_READ_ONLY", result.errorCode());
        verifyNoInteractions(store);
    }

    @Test
    void rejectsMalformedRunId() {
        var proposal = proposal("SELECT 1");
        proposal.put("run_id", "not-a-number");
        var result = gateway.execute(proposal);
        assertEquals("RUN_ID_INVALID", result.errorCode());
        verifyNoInteractions(store);
    }

    @Test
    void executesReadAndAuditsIt() {
        when(store.runExists(42L)).thenReturn(true);
        when(store.executeRead("SELECT 1")).thenReturn(List.of(Map.of("value", 1)));
        var result = gateway.execute(proposal("SELECT 1"));
        assertEquals("succeeded", result.status());
        assertEquals(1, result.rows().size());
        verify(store).audit(any(ToolGatewayStore.Audit.class));
    }

    private static Map<String, Object> proposal(String sql) {
        return new java.util.HashMap<>(
                Map.of(
                        "proposal_id",
                        "proposal-1",
                        "run_id",
                        "42",
                        "proposal_type",
                        "sql_read",
                        "payload",
                        Map.of("statement", sql)));
    }
}
