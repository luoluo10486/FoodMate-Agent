package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.application.runtime.service.RuntimeDlqReplayService;

/** DLQ 重放任务摘要，不返回原消息载荷。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record DlqReplayResponse(
        long replayId, long dlqId, String status, String originalMessageId) {
    public static DlqReplayResponse from(RuntimeDlqReplayService.ReplayResult result) {
        return new DlqReplayResponse(
                result.replayId(), result.dlqId(), result.status(), result.originalMessageId());
    }
}
