package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record StatusUpdateResponse(boolean updated, String status, long revision) {
    public StatusUpdateResponse(boolean updated, String status) {
        this(updated, status, 0);
    }
}
