package com.foodmate.api.request;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RegisterRequest(
        @NotBlank @Size(max = 64) String username,
        @NotBlank @Email String email,
        @NotBlank @Size(min = 8, max = 128) String password,
        @Size(max = 128) String nickname) {}
