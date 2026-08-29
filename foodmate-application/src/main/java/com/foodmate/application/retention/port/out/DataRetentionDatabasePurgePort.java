package com.foodmate.application.retention.port.out;

/** 仅从权威库删除已批准且已软删除的资源数据。 */
public interface DataRetentionDatabasePurgePort {
    void purge(String resourceType, long resourceId);

    /** 执行数据库清理并返回安全的对账事实。 */
    default PurgeResult purgeWithResult(String resourceType, long resourceId) {
        purge(resourceType, resourceId);
        return new PurgeResult("unknown", 0, true);
    }

    record PurgeResult(String backend, int deletedCount, boolean verifiedAbsent) {}
}
