package com.foodmate.shared.id;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * SnowflakeIdGenerator 的单元测试。
 */
class SnowflakeIdGeneratorTest {
    @Test
    void createsIncreasingPositiveIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1);

        long first = generator.nextId();
        long second = generator.nextId();

        assertTrue(first > 0);
        assertTrue(second > first);
    }

    @Test
    void rejectsInvalidWorkerId() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1024));
    }
}
