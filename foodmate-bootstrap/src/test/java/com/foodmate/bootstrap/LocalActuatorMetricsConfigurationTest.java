package com.foodmate.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

class LocalActuatorMetricsConfigurationTest {
    @Test
    void localProfileExposesMetricsAndHealthProbes() throws IOException {
        List<PropertySource<?>> sources =
                new YamlPropertySourceLoader()
                        .load("application-local", new ClassPathResource("application-local.yml"));

        assertThat(sources).isNotEmpty();
        PropertySource<?> properties = sources.get(0);
        assertThat(properties.getProperty("management.endpoints.web.exposure.include"))
                .as("local Actuator web exposure")
                .isEqualTo("health,info,metrics");
        assertThat(properties.getProperty("management.endpoint.health.probes.enabled"))
                .as("local liveness/readiness probes")
                .isEqualTo(true);
        assertThat(properties.getProperty("management.metrics.tags.application"))
                .as("local metrics application tag")
                .isEqualTo("${spring.application.name:foodmate}");
    }
}
