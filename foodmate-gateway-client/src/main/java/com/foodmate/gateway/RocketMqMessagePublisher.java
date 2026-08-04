package com.foodmate.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.runtime.port.out.MessagePublisherPort;
import com.foodmate.shared.runtime.RuntimeException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.exception.RemotingException;

/** RocketMQ adapter for application-level message publication. */
public final class RocketMqMessagePublisher implements MessagePublisherPort {
    private final DefaultMQProducer producer;
    private final RocketMqSettings settings;
    private final ObjectMapper mapper;
    private final String contractVersion;

    public RocketMqMessagePublisher(
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
    public PublishResult publish(PublishRequest request) {
        try {
            Message message =
                    new Message(request.topic(), request.body().getBytes(StandardCharsets.UTF_8));
            if (request.key() != null && !request.key().isBlank()) message.setKeys(request.key());
            message.putUserProperty("foodmate_schema_version", contractVersion);
            for (Map.Entry<String, String> property : request.properties().entrySet()) {
                message.putUserProperty(property.getKey(), property.getValue());
            }
            SendResult result =
                    producer.send(
                            message,
                            this::selectByKey,
                            request.key() == null ? "" : request.key(),
                            settings.sendTimeoutMs());
            if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                throw new RuntimeException(
                        "RUNTIME_UNAVAILABLE",
                        "broker did not confirm the message: "
                                + (result == null ? "null" : result.getSendStatus()));
            }
            return new PublishResult(result.getMsgId());
        } catch (RuntimeException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("RUNTIME_UNAVAILABLE", "publish interrupted");
        } catch (MQClientException | MQBrokerException | RemotingException exception) {
            throw new RuntimeException("RUNTIME_UNAVAILABLE", safeMessage(exception));
        } catch (Exception exception) {
            throw new RuntimeException("RUNTIME_CONTRACT_INVALID", safeMessage(exception));
        }
    }

    private MessageQueue selectByKey(
            java.util.List<MessageQueue> queues, Message message, Object key) {
        if (queues.isEmpty()) throw new IllegalStateException("RocketMQ returned no message queue");
        return queues.get(Math.floorMod(String.valueOf(key).hashCode(), queues.size()));
    }

    private String safeMessage(Exception exception) {
        String value = exception.getMessage();
        String text = value == null ? exception.getClass().getSimpleName() : value;
        return text.substring(0, Math.min(500, text.length()));
    }
}
