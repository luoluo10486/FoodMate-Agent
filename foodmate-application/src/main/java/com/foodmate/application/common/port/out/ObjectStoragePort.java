package com.foodmate.application.common.port.out;

import java.io.InputStream;
import java.time.Duration;

/** 应用层拥有的对象存储端口，供多个业务模块复用。 */
public interface ObjectStoragePort {
    void ensureBucket(String bucket);

    void put(String bucket, String key, InputStream input, long size, String contentType);

    void delete(String bucket, String key);

    /** 返回清理操作后对象是否仍然存在。 */
    boolean exists(String bucket, String key);

    String presignedGet(String bucket, String key, Duration expiry);
}
