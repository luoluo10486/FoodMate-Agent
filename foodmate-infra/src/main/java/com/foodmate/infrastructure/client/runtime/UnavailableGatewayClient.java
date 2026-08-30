package com.foodmate.infrastructure.client.runtime;

import com.foodmate.application.runtime.port.out.RuntimeGatewayPort;
import com.foodmate.shared.runtime.CancelCommand;
import com.foodmate.shared.runtime.RunCommand;

/** Runtime 集成关闭时使用的显式失败关闭网关。 */
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
