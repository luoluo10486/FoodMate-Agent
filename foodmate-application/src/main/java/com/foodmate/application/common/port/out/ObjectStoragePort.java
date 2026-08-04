package com.foodmate.application.common.port.out;

import java.io.InputStream;
import java.time.Duration;

/** Application-owned contract for object storage used by multiple business modules. */
public interface ObjectStoragePort {
    void ensureBucket(String bucket);

    void put(String bucket, String key, InputStream input, long size, String contentType);

    void delete(String bucket, String key);

    String presignedGet(String bucket, String key, Duration expiry);
}
