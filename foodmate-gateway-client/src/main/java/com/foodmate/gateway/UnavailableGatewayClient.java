package com.foodmate.gateway;

import com.foodmate.shared.runtime.CancelCommand;
import com.foodmate.shared.runtime.RunCommand;

/** Explicit fail-closed client used when the Runtime integration is disabled. */
public final class UnavailableGatewayClient implements GatewayClient {
    @Override
    public Response dispatch(RunCommand command) {
        throw unavailable();
    }

    @Override
    public Response cancel(CancelCommand command) {
        throw unavailable();
    }

    private static com.foodmate.shared.runtime.RuntimeException unavailable() {
        return new com.foodmate.shared.runtime.RuntimeException("RUNTIME_UNAVAILABLE", "runtime is not configured");
    }
}
