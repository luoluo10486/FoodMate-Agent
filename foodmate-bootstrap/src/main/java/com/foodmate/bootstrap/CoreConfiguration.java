package com.foodmate.bootstrap;

import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.id.SnowflakeIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CoreConfiguration {
    @Bean
    public IdGenerator idGenerator() {
        return new SnowflakeIdGenerator(1);
    }
}
