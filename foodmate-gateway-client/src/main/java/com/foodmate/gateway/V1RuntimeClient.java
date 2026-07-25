package com.foodmate.gateway;

import com.foodmate.shared.runtime.V1CancelCommand;
import com.foodmate.shared.runtime.V1RunCommand;

public interface V1RuntimeClient {
    Response dispatch(V1RunCommand command);
    Response cancel(V1CancelCommand command);
    record Response(int status, String body) {}
}
