package com.foodmate.infrastructure.messaging.rocketmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.runtime.port.out.RuntimeClientPort;
import com.foodmate.shared.runtime.RuntimeException;
import com.foodmate.shared.runtime.V1CancelCommand;
import com.foodmate.shared.runtime.V1RunCommand;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.exception.RemotingException;

/** RocketMQ implementation of the V1 runtime client port. */
public final class V1RocketMqRuntimeClient implements RuntimeClientPort, AutoCloseable {
    private final DefaultMQProducer producer;
    private final RocketMqSettings settings;
    private final ObjectMapper mapper;
    private final String contractVersion;

    public V1RocketMqRuntimeClient(
            DefaultMQProducer producer,
            RocketMqSettings settings,
            ObjectMapper mapper,
            String contractVersion) {
        this.producer = producer;
        this.settings = settings;
        this.mapper = mapper.findAndRegisterModules();
        this.contractVersion =
                contractVersion == null || contractVersion.isBlank() ? "v1" : contractVersion;
    }

    @Override
    public RuntimeClientPort.Response dispatch(V1RunCommand command) {
        return send(
                command.runId(),
                command.dispatchId(),
                command.attempt(),
                "RunCommand",
                command.requestHash(),
                command);
    }

    @Override
    public RuntimeClientPort.Response cancel(V1CancelCommand command) {
        return send(
                command.runId(),
                command.dispatchId(),
                command.attempt(),
                "CancelCommand",
                command.requestHash(),
                command);
    }

    private RuntimeClientPort.Response send(
            String runId,
            String dispatchId,
            int attempt,
            String messageType,
            String requestHash,
            Object envelope) {
        try {
            byte[] body = mapper.writeValueAsBytes(envelope);
            Message message = new Message(settings.commandTopic(), body);
            message.setKeys(runId);
            message.putUserProperty("foodmate_message_type", messageType);
            message.putUserProperty("foodmate_schema_version", contractVersion);
            message.putUserProperty("foodmate_run_id", runId);
            message.putUserProperty("foodmate_dispatch_id", dispatchId);
            message.putUserProperty("foodmate_attempt", Integer.toString(attempt));
            message.putUserProperty("foodmate_request_hash", requestHash);

            SendResult result =
                    producer.send(message, this::selectByRunId, runId, settings.sendTimeoutMs());
            if (result == null || result.getSendStatus() != SendStatus.SEND_OK)
                throw new RuntimeException(
                        "RUNTIME_UNAVAILABLE",
                        "broker did not confirm the message: "
                                + (result == null ? "null" : result.getSendStatus()));
            return new RuntimeClientPort.Response(
                    202, new String(body, StandardCharsets.UTF_8), result.getMsgId());
        } catch (RuntimeException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("RUNTIME_UNAVAILABLE", "publish interrupted");
        } catch (MQClientException | MQBrokerException | RemotingException exception) {
            throw new RuntimeException("RUNTIME_UNAVAILABLE", safeMessage(exception));
        } catch (JsonProcessingException exception) {
            throw new RuntimeException("RUNTIME_CONTRACT_INVALID", safeMessage(exception));
        } catch (java.lang.RuntimeException exception) {
            throw new RuntimeException("RUNTIME_CONTRACT_INVALID", safeMessage(exception));
        }
    }

    private MessageQueue selectByRunId(List<MessageQueue> queues, Message message, Object runId) {
        int index = Math.floorMod(runId.hashCode(), queues.size());
        return queues.get(index);
    }

    private String safeMessage(Exception exception) {
        String value = exception.getMessage();
        String text = value == null ? exception.getClass().getSimpleName() : value;
        return text.substring(0, Math.min(500, text.length()));
    }

    @Override
    public void close() {
        producer.shutdown();
    }
}
