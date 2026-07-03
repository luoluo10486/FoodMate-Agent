package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class SoftDeleteRepositorySupportTest {
    @Test
    void softDeleteMarksAuditFieldsAndUpdatesEntity() {
        TestPo po = new TestPo();
        Instant deletedAt = Instant.parse("2026-07-03T00:00:00Z");
        TestRepository repository = repositoryReturning(1);

        int updated = repository.delete(po, 100L, deletedAt);

        assertEquals(1, updated);
        assertTrue(po.getIsDeleted());
        assertEquals(100L, po.getDeletedBy());
        assertEquals(deletedAt, po.getDeletedAt());
        assertSame(po, repository.updatedEntity.get());
    }

    @Test
    void restoreClearsDeleteFieldsAndUpdatesEntity() {
        TestPo po = new TestPo();
        po.markDeleted(100L, Instant.parse("2026-07-03T00:00:00Z"));
        TestRepository repository = repositoryReturning(1);
        RestoreCommand command = new RestoreCommand("test", 1L, 200L, "req_restore", "trace_restore");

        int updated = repository.restoreEntity(po, command);

        assertEquals(1, updated);
        assertFalse(po.getIsDeleted());
        assertEquals(200L, po.getUpdatedBy());
        assertNull(po.getDeletedBy());
        assertNull(po.getDeletedAt());
        assertSame(po, repository.updatedEntity.get());
    }

    @SuppressWarnings("unchecked")
    private TestRepository repositoryReturning(int updateCount) {
        AtomicReference<TestPo> updatedEntity = new AtomicReference<>();
        BaseMapper<TestPo> mapper = (BaseMapper<TestPo>) Proxy.newProxyInstance(
                BaseMapper.class.getClassLoader(),
                new Class<?>[]{BaseMapper.class},
                (proxy, method, args) -> {
                    if ("updateById".equals(method.getName())) {
                        updatedEntity.set((TestPo) args[0]);
                        return updateCount;
                    }
                    if ("toString".equals(method.getName())) {
                        return "TestBaseMapper";
                    }
                    throw new UnsupportedOperationException(method.getName());
                }
        );
        return new TestRepository(mapper, updatedEntity);
    }

    private static final class TestRepository extends SoftDeleteRepositorySupport<TestPo> {
        private final AtomicReference<TestPo> updatedEntity;

        private TestRepository(BaseMapper<TestPo> mapper, AtomicReference<TestPo> updatedEntity) {
            super(mapper);
            this.updatedEntity = updatedEntity;
        }

        private int delete(TestPo entity, Long operatorId, Instant deletedAt) {
            return softDelete(entity, operatorId, deletedAt);
        }

        private int restoreEntity(TestPo entity, RestoreCommand command) {
            return restore(entity, command);
        }
    }

    private static final class TestPo extends BasePo {
    }
}
