package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.foodmate.application.runtime.RuntimeGatewayService;
import com.foodmate.shared.runtime.RunCommand;
import com.foodmate.shared.runtime.RunEvent;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import com.foodmate.gateway.GatewayClient;
import com.foodmate.shared.runtime.CancelCommand;

/** Verifies that runtime status and canonical events survive a service restart. */
@SpringBootTest
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "foodmate.local-e2e", matches = "true")
class LocalRuntimeRecoveryTest {
    @TestConfiguration
    static class RuntimeTestConfiguration {
        @Bean
        GatewayClient gatewayClient() {
            return new GatewayClient() {
                public Response dispatch(RunCommand command) { return new Response(202, "{}"); }
                public Response cancel(CancelCommand command) { return new Response(202, "{}"); }
            };
        }
    }

    @Autowired RuntimeGatewayService runtime;
    @Autowired JdbcTemplate jdbc;

    @Test
    void runtimeRowsRemainReadableAfterProcessStateIsDiscarded() {
        String runId = "recovery-" + System.currentTimeMillis();
        RunCommand command = new RunCommand("dispatch-" + runId, runId, "recovery", Instant.now().plusSeconds(60), 1);
        runtime.dispatch(command);
        runtime.event(new RunEvent("event-1-" + runId, runId, 1, RunEvent.State.DISPATCHED, java.util.Map.of(), Instant.now()));
        runtime.event(new RunEvent("event-2-" + runId, runId, 2, RunEvent.State.RUNNING, java.util.Map.of(), Instant.now()));

        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM runtime_dispatches WHERE run_id=?", Integer.class, runId));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM runtime_event_inbox WHERE run_id=?", Integer.class, runId));

        List<String> states = jdbc.query("SELECT state FROM runtime_event_inbox WHERE run_id=? ORDER BY event_seq", (rs, row) -> rs.getString(1), runId);
        assertEquals(List.of("DISPATCHED", "RUNNING"), states);
        assertTrue(jdbc.queryForObject("SELECT status FROM runtime_runs WHERE run_id=?", String.class, runId).equals("RUNNING"));
    }
}
