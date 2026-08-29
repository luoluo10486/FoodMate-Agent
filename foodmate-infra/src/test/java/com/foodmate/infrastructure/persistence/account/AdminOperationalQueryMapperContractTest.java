package com.foodmate.infrastructure.persistence.account;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.Stream;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

/** 验证运营 Trace 列表使用与详情一致的事实统计口径。 */
class AdminOperationalQueryMapperContractTest {
    @Test
    void traceListCountsEveryPersistedTraceFact() {
        String sql =
                Arrays.stream(AdminOperationalQueryMapper.class.getDeclaredMethods())
                        .filter(method -> method.getName().equals("traces"))
                        .flatMap(method -> Stream.of(method.getAnnotation(Select.class)))
                        .map(Select::value)
                        .flatMap(Arrays::stream)
                        .reduce("", (left, right) -> left + " " + right)
                        .toLowerCase();

        assertTrue(sql.contains("count(*) from runtime_event_inbox_v2"));
        assertTrue(sql.contains("count(*) from agent_run_sse_outbox"));
        assertTrue(sql.contains("count(*) from sql_query_audits"));
        assertTrue(sql.contains("count(*) from operation_audits"));
    }
}
