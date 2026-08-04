package com.foodmate.infrastructure.client.runtime;

import com.foodmate.application.runtime.port.out.RuntimeGatewayPort;
import com.foodmate.shared.runtime.CancelCommand;
import com.foodmate.shared.runtime.RunCommand;

/** Explicit fail-closed gateway used when the Runtime integration is disabled. */
public final class UnavailableGatewayClient implements RuntimeGatewayPort {
    @Override
    public RuntimeGatewayPort.Response dispatch(RunCommand command) {
        throw unavailable();
    }

    @Override
    public RuntimeGatewayPort.Response cancel(CancelCommand command) {
        throw unavailable();
    }

    private static com.foodmate.shared.runtime.RuntimeException unavailable() {
        return new com.foodmate.shared.runtime.RuntimeException(
                "RUNTIME_UNAVAILABLE", "runtime is not configured");
    }
}
