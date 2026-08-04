package com.foodmate.application.account.port.out;

import java.io.InputStream;
import java.time.Duration;

/** Object storage boundary for account files and export archives. */
public interface ObjectStoragePort {
    void ensureBucket(String bucket);

    void put(String bucket, String key, InputStream input, long size, String contentType);

    void delete(String bucket, String key);

    String presignedGet(String bucket, String key, Duration expiry);
}
