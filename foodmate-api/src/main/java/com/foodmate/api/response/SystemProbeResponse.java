package com.foodmate.api.response;

import java.time.Instant;

public record SystemProbeResponse(String status, String echo, Instant timestamp) {}
