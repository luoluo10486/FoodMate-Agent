package com.foodmate.application.runtime.port.out;

import com.foodmate.shared.runtime.V1CancelCommand;
import com.foodmate.shared.runtime.V1RunCommand;

/** V1 Runtime 客户端端口；支持 HTTP 和 RocketMQ 两种传输实现。 */
public interface RuntimeClientPort {
    Response dispatch(V1RunCommand command);

    Response cancel(V1CancelCommand command);

    /** 传输结果；只有消息传输可能返回 messageId。 */
    record Response(int status, String body, String messageId) {
        public Response(int status, String body) {
            this(status, body, null);
        }
    }
}
