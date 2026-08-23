package com.foodmate.application.retention.port.out;

/** Deletes only approved, already soft-deleted resource data from the authority store. */
public interface DataRetentionDatabasePurgePort {
    void purge(String resourceType, long resourceId);
}
