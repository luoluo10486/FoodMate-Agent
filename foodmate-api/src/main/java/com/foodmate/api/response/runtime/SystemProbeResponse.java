package com.foodmate.api.response.runtime;

import java.time.Instant;

/** 系统探针响应。 */
public record SystemProbeResponse(String status, String echo, Instant timestamp) {}
