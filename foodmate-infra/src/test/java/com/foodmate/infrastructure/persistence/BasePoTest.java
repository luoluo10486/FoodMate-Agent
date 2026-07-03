package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.annotation.TableLogic;
import java.lang.reflect.Field;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BasePoTest {
    @Test
    void hasMybatisPlusLogicDeleteAnnotation() throws Exception {
        Field field = BasePo.class.getDeclaredField("isDeleted");

        assertNotNull(field.getAnnotation(TableLogic.class));
    }

    @Test
    void marksAndRestoresSoftDeleteFields() {
        TestPo po = new TestPo();
        Instant deletedAt = Instant.parse("2026-07-03T00:00:00Z");

        po.markDeleted(100L, deletedAt);

        assertTrue(po.getIsDeleted());
        assertEquals(100L, po.getDeletedBy());
        assertEquals(deletedAt, po.getDeletedAt());

        po.restore(200L);

        assertFalse(po.getIsDeleted());
        assertEquals(200L, po.getUpdatedBy());
        assertNull(po.getDeletedBy());
        assertNull(po.getDeletedAt());
    }

    private static void assertEquals(Object expected, Object actual) {
        org.junit.jupiter.api.Assertions.assertEquals(expected, actual);
    }

    private static final class TestPo extends BasePo {
    }
}

