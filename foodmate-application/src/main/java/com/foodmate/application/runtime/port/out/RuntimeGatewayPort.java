package com.foodmate.application.runtime.port.out;

import com.foodmate.shared.runtime.CancelCommand;
import com.foodmate.shared.runtime.RunCommand;

/** Runtime 兼容 HTTP 调用端口；具体传输由基础设施层提供。 */
public interface RuntimeGatewayPort {
    Response dispatch(RunCommand command);

    Response cancel(CancelCommand command);

    record Response(int status, String body) {}
}
