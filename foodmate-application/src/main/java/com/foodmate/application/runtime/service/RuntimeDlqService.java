package com.foodmate.application.runtime.service;

import com.foodmate.gateway.MqMessageHandler;

/** Records failed message deliveries and reconciles their durable outcome. */
public interface RuntimeDlqService extends MqMessageHandler {
    void reconcile();
}
