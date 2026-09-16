package com.foodmate.application.runtime.port.out;

import java.time.Instant;
import java.util.List;

/** 持久化层提供的工具注册表只读契约。 */
public interface ToolRegistryRepository {
    List<ToolDefinition> findAll();

    ToolDefinition findCurrent(String name);

    ToolDefinition findVersion(String name, String version);

    record ToolDefinition(
            long toolId,
            String name,
            String displayName,
            String description,
            String category,
            String riskLevel,
            String availabilityScope,
            String status,
            String currentVersion,
            String version,
            String inputSchemaJson,
            String outputSchemaJson,
            String permissionsJson,
            int timeoutMs,
            boolean retryable,
            boolean idempotent,
            Instant publishedAt,
            long revision,
            boolean skippable) {
        /** 保持现有注册表测试和本地适配器构造方式兼容。 */
        public ToolDefinition(
                long toolId,
                String name,
                String displayName,
                String description,
                String category,
                String riskLevel,
                String availabilityScope,
                String status,
                String currentVersion,
                String version,
                String inputSchemaJson,
                String outputSchemaJson,
                String permissionsJson,
                int timeoutMs,
                boolean retryable,
                boolean idempotent,
                Instant publishedAt,
                long revision) {
            this(
                    toolId,
                    name,
                    displayName,
                    description,
                    category,
                    riskLevel,
                    availabilityScope,
                    status,
                    currentVersion,
                    version,
                    inputSchemaJson,
                    outputSchemaJson,
                    permissionsJson,
                    timeoutMs,
                    retryable,
                    idempotent,
                    publishedAt,
                    revision,
                    false);
        }
    }
}
