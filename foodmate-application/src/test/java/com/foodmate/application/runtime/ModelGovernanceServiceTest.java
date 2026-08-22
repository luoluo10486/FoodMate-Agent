package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.foodmate.application.runtime.port.out.ModelGovernanceRepository.ModelGovernanceSnapshot;
import com.foodmate.application.runtime.service.ModelGovernanceService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class ModelGovernanceServiceTest {
    @Test
    void localStubUsesDeterministicNonSecretSnapshot() {
        ModelGovernanceService service =
                new ModelGovernanceService(
                        null,
                        "deterministic",
                        "stub-chat-v1",
                        "stub-route-v1",
                        "stub-price-v1",
                        "stub-budget-v1",
                        30000,
                        new BigDecimal("0.50"),
                        12,
                        2,
                        15000);

        ModelGovernanceSnapshot snapshot = service.resolve("agent_run", "chat");

        assertEquals("deterministic", snapshot.providerCode());
        assertEquals("stub-chat-v1", snapshot.modelName());
        assertEquals("stub-route-v1", snapshot.routeVersion());
        assertEquals("stub-price-v1", snapshot.priceVersion());
        assertEquals("stub-budget-v1", snapshot.budgetPolicyVersion());
        assertEquals(new BigDecimal("0.50"), snapshot.maxCostCny());
        assertNull(snapshot.fallbackProviderCode());
    }

    @Test
    void activeRepositorySnapshotWinsOverEnvironmentDefaults() {
        ModelGovernanceSnapshot governed =
                new ModelGovernanceSnapshot(
                        "agent_run",
                        "chat",
                        "route-7",
                        "provider-a",
                        "model-a",
                        "provider-b",
                        "model-b",
                        "price-4",
                        BigDecimal.ONE,
                        BigDecimal.TEN,
                        "budget-3",
                        100,
                        BigDecimal.ONE,
                        2,
                        1,
                        5000,
                        java.time.Instant.parse("2026-08-01T00:00:00Z"));
        ObjectProvider<com.foodmate.application.runtime.port.out.ModelGovernanceRepository>
                provider = new FixedProvider(governed);
        ModelGovernanceService service =
                new ModelGovernanceService(
                        provider,
                        "deterministic",
                        "fallback",
                        "env-route",
                        "env-price",
                        "env-budget",
                        30000,
                        new BigDecimal("0.50"),
                        12,
                        2,
                        15000);

        ModelGovernanceSnapshot snapshot = service.resolve("agent_run", "chat");

        assertEquals("provider-a", snapshot.providerCode());
        assertEquals("route-7", snapshot.routeVersion());
        assertEquals(100, snapshot.maxTotalTokens());
    }

    private static final class FixedProvider
            implements ObjectProvider<
                    com.foodmate.application.runtime.port.out.ModelGovernanceRepository> {
        private final ModelGovernanceSnapshot snapshot;

        private FixedProvider(ModelGovernanceSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public com.foodmate.application.runtime.port.out.ModelGovernanceRepository getObject(
                Object... args) {
            return (scene, modelType) -> snapshot;
        }

        @Override
        public com.foodmate.application.runtime.port.out.ModelGovernanceRepository
                getIfAvailable() {
            return (scene, modelType) -> snapshot;
        }

        @Override
        public com.foodmate.application.runtime.port.out.ModelGovernanceRepository getIfUnique() {
            return getIfAvailable();
        }

        @Override
        public java.util.stream.Stream<
                        com.foodmate.application.runtime.port.out.ModelGovernanceRepository>
                orderedStream() {
            return java.util.stream.Stream.of(getIfAvailable());
        }

        @Override
        public java.util.stream.Stream<
                        com.foodmate.application.runtime.port.out.ModelGovernanceRepository>
                stream() {
            return orderedStream();
        }
    }
}
