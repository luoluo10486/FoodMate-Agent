package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.foodmate.application.runtime.port.out.ToolGatewayPort;
import com.foodmate.application.runtime.service.ToolGatewayService;
import com.foodmate.application.runtime.service.impl.ToolGatewayServiceImpl;
import com.foodmate.shared.id.IdGenerator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolGatewayServiceTest {
    private final ToolGatewayPort store = mock(ToolGatewayPort.class);
    private final ToolGatewayService gateway =
            new ToolGatewayServiceImpl(store, (IdGenerator) () -> 99L);

    @Test
    void rejectsWriteSqlBeforeDatabaseExecution() {
        var result = gateway.execute(proposal("UPDATE agent_runs SET status='completed'"));
        assertEquals("rejected", result.status());
        assertEquals("SQL_PROPOSAL_NOT_READ_ONLY", result.errorCode());
        verifyNoInteractions(store);
    }

    @Test
    void rejectsMalformedRunId() {
        var result =
                gateway.execute(
                        new ToolGatewayService.ProposalCommand(
                                "proposal-1",
                                "not-a-number",
                                "sql_read",
                                "v1",
                                new ToolGatewayService.ProposalPayload(
                                        "SELECT 1", "invocation-1")));
        assertEquals("RUN_ID_INVALID", result.errorCode());
        verifyNoInteractions(store);
    }

    @Test
    void rejectsProposalWithoutInvocationIdBeforeDatabaseExecution() {
        var result =
                gateway.execute(
                        new ToolGatewayService.ProposalCommand(
                                "proposal-1",
                                "42",
                                "sql_read",
                                "v1",
                                new ToolGatewayService.ProposalPayload("SELECT 1", null)));
        assertEquals("PROPOSAL_NOT_ALLOWED", result.errorCode());
        verifyNoInteractions(store);
    }

    @Test
    void executesReadAndAuditsIt() {
        when(store.runExists(42L)).thenReturn(true);
        when(store.executeRead("SELECT 1"))
                .thenReturn(List.of(JsonNodeFactory.instance.objectNode().put("value", 1)));
        var result = gateway.execute(proposal("SELECT 1"));
        assertEquals("succeeded", result.status());
        assertEquals(1, result.rows().size());
        verify(store).audit(any(ToolGatewayPort.Audit.class));
    }

    private static ToolGatewayService.ProposalCommand proposal(String sql) {
        return new ToolGatewayService.ProposalCommand(
                "proposal-1",
                "42",
                "sql_read",
                "v1",
                new ToolGatewayService.ProposalPayload(sql, "invocation-1"));
    }
}
