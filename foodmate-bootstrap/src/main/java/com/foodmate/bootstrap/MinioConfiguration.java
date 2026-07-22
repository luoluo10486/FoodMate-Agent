package com.foodmate.bootstrap;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinioConfiguration {
    @Bean
    MinioClient minioClient(@Value("${foodmate.storage.endpoint:http://localhost:9000}") String endpoint,
                            @Value("${foodmate.storage.access-key:minioadmin}") String accessKey,
                            @Value("${foodmate.storage.secret-key:minioadmin}") String secretKey) {
        return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }
}
