package com.foodmate.infrastructure.persistence.knowledge;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Update;
import org.junit.jupiter.api.Test;

/** 校验知识索引 Outbox 的重试 SQL 不会重排历史事实。 */
class KnowledgeMapperContractTest {
    @Test
    void manualRetryStartsAtAttemptOneWhenItCopiesTheLatestPayload() throws Exception {
        String sql =
                Method.class
                        .cast(
                                KnowledgeMapper.class.getMethod(
                                        "insertIndexOutbox", long.class, long.class, String.class))
                        .getAnnotation(Insert.class)
                        .value()[0];

        assertTrue(sql.contains("requested_payload='{}'::jsonb"));
        assertTrue(sql.contains("jsonb_set(payload,'{attempt}'"));
        assertTrue(sql.contains("jsonb_exists(payload,'attempt')"));
        assertTrue(!sql.contains("payload ? 'attempt'"));
        assertTrue(sql.contains("'1'::jsonb"));
        assertTrue(sql.contains("ORDER BY outbox_id DESC LIMIT 1"));
    }

    @Test
    void automaticRetryTargetsTheLatestPublishedFactForThatAttempt() throws Exception {
        String sql =
                KnowledgeMapper.class
                        .getMethod(
                                "requeueIndexOutbox",
                                long.class,
                                int.class,
                                int.class,
                                String.class)
                        .getAnnotation(Update.class)
                        .value()[0];

        assertTrue(sql.contains("status='published'"));
        assertTrue(sql.contains("payload_json->>'attempt'"));
        assertTrue(sql.contains("END=(#{attempt} - 1)"));
        assertTrue(sql.contains("ORDER BY outbox_id DESC LIMIT 1"));
        assertTrue(sql.contains("FROM candidate"));
    }

    @Test
    void indexedResultKeepsProviderTracePersistenceCompatibleWithPreV29Databases() throws Exception {
        String sql =
                KnowledgeMapper.class
                        .getMethod(
                                "markItemIndexed",
                                long.class,
                                long.class,
                                int.class,
                                int.class,
                                String.class,
                                long.class,
                                java.math.BigDecimal.class,
                                String.class,
                                String.class)
                        .getAnnotation(Update.class)
                        .value()[0];

        assertTrue(!sql.contains("provider_trace_id=#{providerTraceId}"));

        String traceSql =
                KnowledgeMapper.class
                        .getMethod("updateProviderTraceId", long.class, String.class)
                        .getAnnotation(Update.class)
                        .value()[0];
        assertTrue(traceSql.contains("provider_trace_id=#{providerTraceId}"));
        assertTrue(traceSql.contains("updated_at=CURRENT_TIMESTAMP"));
    }
}
