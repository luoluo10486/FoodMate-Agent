package com.foodmate.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.foodmate.shared.runtime.RunCommand;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class UnavailableGatewayClientTest {
    @Test
    void alwaysFailsClosed() {
        var client = new UnavailableGatewayClient();
        var exception = assertThrows(com.foodmate.shared.runtime.RuntimeException.class,
                () -> client.dispatch(new RunCommand("d1", "r1", "hello", Instant.now().plusSeconds(5), 1)));
        assertEquals("RUNTIME_UNAVAILABLE", exception.code());
    }
}
