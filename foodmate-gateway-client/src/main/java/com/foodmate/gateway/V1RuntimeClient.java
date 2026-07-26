package com.foodmate.gateway;

import com.foodmate.shared.runtime.V1CancelCommand;
import com.foodmate.shared.runtime.V1RunCommand;

public interface V1RuntimeClient {
    Response dispatch(V1RunCommand command);
    Response cancel(V1CancelCommand command);

    /** 传输结果。{@code messageId} 只有 RocketMQ 通道会填写，HTTP 通道为 null。 */
    record Response(int status, String body, String messageId) {
        public Response(int status, String body) { this(status, body, null); }
    }
}
