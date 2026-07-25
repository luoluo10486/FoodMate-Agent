package com.foodmate.bootstrap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.gateway.GatewayClient;
import com.foodmate.gateway.HttpGatewayClient;
import com.foodmate.gateway.UnavailableGatewayClient;
import com.foodmate.gateway.V1HttpRuntimeClient;
import com.foodmate.gateway.V1RuntimeClient;
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
    GatewayClient gatewayClient(@Value("${foodmate.runtime.agent-base-url}") URI baseUrl, @Value("${foodmate.runtime.service-jwt.enabled:false}") boolean jwtEnabled, @Value("${foodmate.runtime.service-jwt.java-private-key:}") String privateKey, @Value("${foodmate.runtime.service-jwt.java-kid:}") String kid, @Value("${foodmate.runtime.contract-version:v1}") String contractVersion, ObjectMapper objectMapper) {
        if (!jwtEnabled) return new UnavailableGatewayClient();
        return new HttpGatewayClient(baseUrl, Duration.ofSeconds(10), HttpClient.newHttpClient(), objectMapper, privateKey, kid, contractVersion);
    }

    @Bean
    V1RuntimeClient v1RuntimeClient(@Value("${foodmate.runtime.agent-base-url}") URI baseUrl,
                                    @Value("${foodmate.runtime.service-jwt.enabled:false}") boolean jwtEnabled,
                                    @Value("${foodmate.runtime.service-jwt.java-private-key:}") String privateKey,
                                    @Value("${foodmate.runtime.service-jwt.java-kid:}") String kid,
                                    @Value("${foodmate.runtime.contract-version:v1}") String contractVersion,
                                    ObjectMapper objectMapper) {
        if (!jwtEnabled) return new V1RuntimeClient() {
            public Response dispatch(com.foodmate.shared.runtime.V1RunCommand command) { throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_UNAVAILABLE", "runtime service is disabled"); }
            public Response cancel(com.foodmate.shared.runtime.V1CancelCommand command) { throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_UNAVAILABLE", "runtime service is disabled"); }
        };
        return new V1HttpRuntimeClient(baseUrl, Duration.ofSeconds(10), HttpClient.newHttpClient(), objectMapper, privateKey, kid, contractVersion);
    }
}
