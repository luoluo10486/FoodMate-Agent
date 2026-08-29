package com.foodmate.bootstrap;

import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.id.SnowflakeIdGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 组装与所选传输方式无关的共享 Runtime 基础设施。 */
@Configuration
public class CoreConfiguration {
    @Bean
    public IdGenerator idGenerator() {
        return new SnowflakeIdGenerator(1);
    }
}
