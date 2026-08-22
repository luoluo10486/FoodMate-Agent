package com.foodmate.infrastructure.persistence.account;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.Stream;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class AdminAuditReportMapperContractTest {
    @Test
    void reportQueriesAreAggregateOnlyAndDoNotSelectPayloadColumns() {
        String sql =
                Arrays.stream(AdminAuditReportMapper.class.getDeclaredMethods())
                        .flatMap(method -> Stream.of(method.getAnnotation(Select.class)))
                        .map(Select::value)
                        .flatMap(Arrays::stream)
                        .reduce("", (left, right) -> left + " " + right)
                        .toLowerCase();

        assertTrue(sql.contains("count(*)"));
        assertTrue(sql.contains("operation_audits"));
        assertTrue(sql.contains("runtime_message_dlq"));
        assertFalse(sql.contains("raw_payload_json"));
        assertFalse(sql.contains("last_error"));
        assertFalse(sql.contains("request_json"));
        assertFalse(sql.contains("response_json"));
    }
}
