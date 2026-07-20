package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.filter.TraceContextFilter;
import com.foodmate.application.runtime.RuntimeGatewayService;
import com.foodmate.gateway.ServiceJwt;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RuntimeGatewayController.class)
@Import({RuntimeGatewayService.class, GlobalExceptionHandler.class, TraceContextFilter.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RuntimeGatewayControllerTest {
    private static final KeyPair JAVA_KEYS = keys();
    private static final KeyPair PYTHON_KEYS = keys();
    @Autowired private MockMvc mockMvc;

    private static final String DISPATCH = "{" +
            "\"dispatch_id\":\"d-http\",\"run_id\":\"r-http\",\"input\":\"hello\"," +
            "\"deadline_at\":\"2099-01-01T00:00:00Z\",\"attempt\":1}";

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("foodmate.runtime.service-jwt.enabled", () -> true);
        registry.add("foodmate.runtime.service-jwt.java-public-key", () -> encoded(JAVA_KEYS.getPublic()));
        registry.add("foodmate.runtime.service-jwt.python-public-key", () -> encoded(PYTHON_KEYS.getPublic()));
    }

    @Test void dispatchRetryIsDuplicateAndChangedBodyConflicts() throws Exception {
        mockMvc.perform(post("/internal/runtime/runs:dispatch").header("Authorization", "Bearer " + javaToken("runtime:dispatch")).contentType(MediaType.APPLICATION_JSON).content(DISPATCH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.duplicate", is(false)));
        mockMvc.perform(post("/internal/runtime/runs:dispatch").header("Authorization", "Bearer " + javaToken("runtime:dispatch")).contentType(MediaType.APPLICATION_JSON).content(DISPATCH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.duplicate", is(true)));
        mockMvc.perform(post("/internal/runtime/runs:dispatch").header("Authorization", "Bearer " + javaToken("runtime:dispatch")).contentType(MediaType.APPLICATION_JSON).content(DISPATCH.replace("hello", "changed")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code", is("CONFLICT")));
    }

    @Test void eventChainReachesSucceededAndRejectsRollback() throws Exception {
        mockMvc.perform(post("/internal/runtime/runs:dispatch").header("Authorization", "Bearer " + javaToken("runtime:dispatch")).contentType(MediaType.APPLICATION_JSON).content(DISPATCH));
        postEvent("e-http-1", 1, "DISPATCHED", null, false);
        postEvent("e-http-2", 2, "RUNNING", null, false);
        postEvent("e-http-3", 3, "SUCCEEDED", "ok", false);
        postEvent("e-http-4", 4, "RUNNING", null, true);
    }

    private void postEvent(String id, int seq, String state, String payload, boolean conflict) throws Exception {
        String json = "{\"event_id\":\"" + id + "\",\"run_id\":\"r-http\",\"event_seq\":" + seq + ",\"state\":\"" + state + "\",\"payload\":" + (payload == null ? "null" : "\"" + payload + "\"") + ",\"occurred_at\":\"2099-01-01T00:00:00Z\"}";
        var result = mockMvc.perform(post("/internal/runtime/runs:events").header("Authorization", "Bearer " + pythonToken("runtime:event")).contentType(MediaType.APPLICATION_JSON).content(json));
        if (conflict) result.andExpect(status().isConflict()).andExpect(jsonPath("$.error.code", is("CONFLICT")));
        else result.andExpect(status().isOk()).andExpect(jsonPath("$.success", is(true)));
    }

    private static String javaToken(String scope) { return ServiceJwt.sign(encoded(JAVA_KEYS.getPrivate()), "foodmate-control-plane", "foodmate-control-plane", scope, "java-test", 60); }
    private static String pythonToken(String scope) { return ServiceJwt.sign(encoded(PYTHON_KEYS.getPrivate()), "foodmate-agent-runtime", "foodmate-control-plane", scope, "python-test", 60); }
    private static String encoded(java.security.Key key) { return Base64.getEncoder().encodeToString(key.getEncoded()); }
    private static KeyPair keys() { try { return KeyPairGenerator.getInstance("Ed25519").generateKeyPair(); } catch (Exception exception) { throw new IllegalStateException(exception); } }
}
