package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 管理端导出下载响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AdminExportDownloadResponse(String downloadUrl) {}
