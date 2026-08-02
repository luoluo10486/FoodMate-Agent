package com.foodmate.api.response.runtime;

import java.time.Instant;

public record SystemProbeResponse(String status, String echo, Instant timestamp) {}
