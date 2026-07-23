package com.foodmate.bootstrap;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfiguration {
    @Bean
    MinioClient minioClient(@Value("${foodmate.storage.endpoint:${MINIO_ENDPOINT:http://localhost:9000}}") String endpoint,
                            @Value("${foodmate.storage.access-key:${MINIO_ACCESS_KEY:minioadmin}}") String accessKey,
                            @Value("${foodmate.storage.secret-key:${MINIO_SECRET_KEY:minioadmin}}") String secretKey) {
        return MinioClient.builder().endpoint(endpoint.trim()).credentials(accessKey.trim(), secretKey.trim()).build();
    }
}
