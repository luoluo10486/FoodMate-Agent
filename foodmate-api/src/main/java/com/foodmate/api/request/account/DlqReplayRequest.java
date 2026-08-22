package com.foodmate.api.request.account;

/** DLQ 重放的显式确认参数；原始消息内容不由请求方提交。 */
public record DlqReplayRequest(boolean confirmed, String confirmationDigest) {}
