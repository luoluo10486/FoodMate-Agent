package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 用户数据导出下载响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ExportDownloadResponse(String downloadUrl) {}
