package com.foodmate.gateway;

import com.foodmate.shared.runtime.CancelCommand;
import com.foodmate.shared.runtime.RunCommand;

public interface GatewayClient {
    Response dispatch(RunCommand command);
    Response cancel(CancelCommand command);
    record Response(int status, String body) {}
}
