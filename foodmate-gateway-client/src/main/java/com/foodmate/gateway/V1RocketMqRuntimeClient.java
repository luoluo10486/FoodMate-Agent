package com.foodmate.gateway;

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

/**
 * Java -> Python 的 RocketMQ 传输客户端（ADR-0005 正式异步主通道）。
 *
 * <p>三条不可违反的规则：
 *
 * <ol>
 *   <li>消息体直接使用 Outbox 中已持久化的 envelope，不重新拼装，重试保持同一 payload 与 request_hash。
 *   <li>用 {@code run_id} 选择队列，保证同一 Run 的命令局部有序；不同 Run 可并行。
 *   <li>只有 Broker 返回 {@link SendStatus#SEND_OK} 才算发布成功，其余状态一律当失败重试。
 * </ol>
 *
 * <p>消息不携带 Service JWT：MQ 通道的身份由部署边界保证，Java 仍然按 {@code run_id} 重新推导可信用户上下文，不信任消息体里的身份字段。
 */
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
            // key 用 run_id：mqadmin queryMsgByKey 可以按 Run 排障。
            message.setKeys(runId);
            // 消费端在解析消息体之前就能按这些属性做幂等登记与 DLQ 归档。
            message.putUserProperty("foodmate_message_type", messageType);
            message.putUserProperty("foodmate_schema_version", contractVersion);
            message.putUserProperty("foodmate_run_id", runId);
            message.putUserProperty("foodmate_dispatch_id", dispatchId);
            message.putUserProperty("foodmate_attempt", Integer.toString(attempt));
            message.putUserProperty("foodmate_request_hash", requestHash);

            SendResult result =
                    producer.send(message, this::selectByRunId, runId, settings.sendTimeoutMs());
            if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                // SLAVE_NOT_AVAILABLE / FLUSH_*_TIMEOUT 都不能视为已持久化确认。
                throw new RuntimeException(
                        "RUNTIME_UNAVAILABLE",
                        "broker did not confirm the message: "
                                + (result == null ? "null" : result.getSendStatus()));
            }
            return new RuntimeClientPort.Response(
                    202, new String(body, StandardCharsets.UTF_8), result.getMsgId());
        } catch (RuntimeException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("RUNTIME_UNAVAILABLE", "publish interrupted");
        } catch (MQClientException | MQBrokerException | RemotingException exception) {
            throw new RuntimeException("RUNTIME_UNAVAILABLE", safeMessage(exception));
        } catch (Exception exception) {
            // Jackson 序列化失败属于契约问题，重试不会成功。
            throw new RuntimeException("RUNTIME_CONTRACT_INVALID", safeMessage(exception));
        }
    }

    /** 同一 run_id 固定落到同一队列，使该 Run 的命令保持投递顺序。 */
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
