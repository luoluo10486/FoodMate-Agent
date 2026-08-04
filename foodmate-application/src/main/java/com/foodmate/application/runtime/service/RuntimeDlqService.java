package com.foodmate.application.runtime.service;

import com.foodmate.application.runtime.messaging.MqMessageHandler;

/** Records failed message deliveries and reconciles their durable outcome. */
public interface RuntimeDlqService extends MqMessageHandler {
    void reconcile();
}
