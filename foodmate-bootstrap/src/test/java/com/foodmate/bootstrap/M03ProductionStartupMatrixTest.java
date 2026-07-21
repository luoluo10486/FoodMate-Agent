package com.foodmate.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

class M03ProductionStartupMatrixTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ValidatorConfiguration.class)
            .withPropertyValues(
                    "spring.profiles.active=prod",
                    "spring.datasource.url=jdbc:postgresql://db.internal:5432/FoodMate",
                    "spring.datasource.username=foodmate_app",
                    "spring.datasource.password=strong-test-password",
                    "foodmate.security.cookie-secure=true",
                    "foodmate.runtime.service-jwt.enabled=false");

    @Test
    void missingDatabaseConfigurationFailsStartup() {
        runner.withPropertyValues("spring.datasource.url=").run(context -> assertThat(context).hasFailed());
    }

    @Test
    void localhostDatabaseFailsStartup() {
        runner.withPropertyValues("spring.datasource.url=jdbc:postgresql://localhost:5432/FoodMate")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void defaultCredentialsFailStartup() {
        runner.withPropertyValues("spring.datasource.username=postgres", "spring.datasource.password=postgres")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void insecureCookieFailsStartup() {
        runner.withPropertyValues("foodmate.security.cookie-secure=false")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void enabledJwtWithoutKeysFailsStartup() {
        runner.withPropertyValues("foodmate.runtime.service-jwt.enabled=true")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void completeProductionConfigurationStarts() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(M03ConfigurationValidator.class);
        });
    }

    @Import(M03ConfigurationValidator.class)
    static class ValidatorConfiguration {}
}
