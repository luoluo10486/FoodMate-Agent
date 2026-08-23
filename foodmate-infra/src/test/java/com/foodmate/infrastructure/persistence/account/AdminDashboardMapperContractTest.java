package com.foodmate.infrastructure.persistence.account;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.stream.Stream;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class AdminDashboardMapperContractTest {
    @Test
    void dashboardDoesNotExposeRawSqlOrObjectStorageKeys() {
        String sql =
                Arrays.stream(AdminDashboardMapper.class.getDeclaredMethods())
                        .flatMap(method -> Stream.of(method.getAnnotation(Select.class)))
                        .map(Select::value)
                        .flatMap(Arrays::stream)
                        .reduce("", (left, right) -> left + " " + right)
                        .toLowerCase();

        assertTrue(sql.contains("md5(coalesce(sql_text,original_question,''))"));
        assertTrue(sql.contains("coalesce(source_name,source_type,'-')"));
        assertFalse(sql.contains("left(coalesce(sql_text,original_question)"));
        assertFalse(sql.contains("storage_key as source"));
    }
}
