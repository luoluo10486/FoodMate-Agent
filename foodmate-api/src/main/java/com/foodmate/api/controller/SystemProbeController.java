package com.foodmate.api.controller;

import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统探针控制器，提供最小可验证的健康接口。
 */
@Validated
@RestController
@RequestMapping("/foodmate/_system")
public class SystemProbeController {
    @GetMapping("/ping")
    public ApiResponse<SystemProbeResponse> ping(
            @RequestParam(defaultValue = "foodmate") @Size(max = 64) String echo
    ) {
        return ApiResponse.success(
                new SystemProbeResponse("ok", echo, Instant.now()),
                TraceContextHolder.currentOrNew()
        );
    }

    /**
     * 系统探针返回体。
     */
    public record SystemProbeResponse(String status, String echo, Instant timestamp) {
    }
}
