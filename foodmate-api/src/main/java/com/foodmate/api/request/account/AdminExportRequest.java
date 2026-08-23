package com.foodmate.api.request.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Safe filters and field selection for a bounded administrator export. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AdminExportRequest(
        @NotBlank @Size(max = 32) String resource,
        @Size(max = 128) String query,
        @Size(max = 32) String status,
        @Size(max = 32) String visibility,
        @Size(max = 32) String sort,
        @Size(max = 4) String direction,
        @Size(max = 16) List<@NotBlank @Size(max = 64) String> fields) {}
