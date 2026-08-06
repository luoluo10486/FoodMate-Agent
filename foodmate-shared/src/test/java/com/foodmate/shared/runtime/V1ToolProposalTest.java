package com.foodmate.shared.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class V1ToolProposalTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void deserializesConfirmationFlagFromWireMessage() throws Exception {
        String json =
                """
                {
                  "schema_version": "v1",
                  "proposal_id": "proposal-1",
                  "request_hash": "sha256:request",
                  "run_id": "run-1",
                  "proposal_type": "sql_read",
                  "requires_confirmation": false,
                  "payload": {
                    "statement": "SELECT 1",
                    "invocation_id": "invocation-1"
                  }
                }
                """;

        V1ToolProposal proposal = mapper.readValue(json, V1ToolProposal.class);

        assertFalse(proposal.requiresConfirmation());
        assertEquals("SELECT 1", proposal.payload().statement());
    }
}
