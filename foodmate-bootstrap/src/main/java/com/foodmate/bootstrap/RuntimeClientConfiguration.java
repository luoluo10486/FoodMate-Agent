package com.foodmate.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.knowledge.port.out.KnowledgeSearchPort;
import com.foodmate.application.runtime.port.out.RuntimeClientPort;
import com.foodmate.application.runtime.port.out.RuntimeGatewayPort;
import com.foodmate.infrastructure.client.runtime.HttpGatewayClient;
import com.foodmate.infrastructure.client.runtime.UnavailableGatewayClient;
import com.foodmate.infrastructure.client.runtime.V1HttpKnowledgeSearchClient;
import com.foodmate.infrastructure.client.runtime.V1HttpRuntimeClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "foodmate.runtime.agent-base-url")
public class RuntimeClientConfiguration {
    @Bean
    RuntimeGatewayPort gatewayClient(
            @Value("${foodmate.runtime.agent-base-url}") URI baseUrl,
            @Value("${foodmate.runtime.service-jwt.enabled:false}") boolean jwtEnabled,
            @Value("${foodmate.runtime.service-jwt.java-private-key:}") String privateKey,
            @Value("${foodmate.runtime.service-jwt.java-kid:}") String kid,
            @Value("${foodmate.runtime.contract-version:v1}") String contractVersion,
            ObjectMapper objectMapper) {
        if (!jwtEnabled) return new UnavailableGatewayClient();
        return new HttpGatewayClient(
                baseUrl,
                Duration.ofSeconds(10),
                HttpClient.newHttpClient(),
                objectMapper,
                privateKey,
                kid,
                contractVersion);
    }

    @Bean
    KnowledgeSearchPort knowledgeSearchClient(
            @Value("${foodmate.runtime.agent-base-url}") URI baseUrl,
            @Value("${foodmate.runtime.service-jwt.enabled:false}") boolean jwtEnabled,
            @Value("${foodmate.runtime.service-jwt.java-private-key:}") String privateKey,
            @Value("${foodmate.runtime.service-jwt.java-kid:}") String kid,
            @Value("${foodmate.runtime.contract-version:v1}") String contractVersion,
            ObjectMapper objectMapper) {
        return new V1HttpKnowledgeSearchClient(
                baseUrl,
                Duration.ofSeconds(10),
                HttpClient.newHttpClient(),
                objectMapper,
                privateKey,
                kid,
                contractVersion,
                jwtEnabled);
    }

    /**
     * HTTP 兼容通道（M1-3）。transport=rocketmq 时不装配，由 {@link RuntimeRocketMqConfiguration} 提供唯一的 {@link
     * RuntimeClientPort}： 配置指南 §5.9 规则 10 要求同一进程不能同时启用 HTTP 与 MQ 业务派发。
     */
    @Bean
    @ConditionalOnProperty(
            name = "foodmate.runtime.transport",
            havingValue = "http",
            matchIfMissing = true)
    RuntimeClientPort v1RuntimeClient(
            @Value("${foodmate.runtime.agent-base-url}") URI baseUrl,
            @Value("${foodmate.runtime.service-jwt.enabled:false}") boolean jwtEnabled,
            @Value("${foodmate.runtime.service-jwt.java-private-key:}") String privateKey,
            @Value("${foodmate.runtime.service-jwt.java-kid:}") String kid,
            @Value("${foodmate.runtime.contract-version:v1}") String contractVersion,
            ObjectMapper objectMapper) {
        // Local profile may deliberately disable JWT; the HTTP transport still remains real.
        return new V1HttpRuntimeClient(
                baseUrl,
                Duration.ofSeconds(10),
                HttpClient.newHttpClient(),
                objectMapper,
                privateKey,
                kid,
                contractVersion,
                jwtEnabled);
    }
}
