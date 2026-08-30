package com.foodmate.application.runtime.service;

import com.foodmate.application.runtime.messaging.MqMessageHandler;

/** 记录失败的消息交付并对账其持久化结果。 */
public interface RuntimeDlqService extends MqMessageHandler {
    void reconcile();
}
