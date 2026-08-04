package com.foodmate.infrastructure.storage;

import com.foodmate.application.common.port.out.ObjectStoragePort;
import io.minio.BucketExistsArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import java.io.InputStream;
import java.time.Duration;
import org.springframework.stereotype.Component;

/** MinIO implementation of the application object storage port. */
@Component
public final class MinioObjectStorageAdapter implements ObjectStoragePort {
    private final MinioClient client;

    public MinioObjectStorageAdapter(MinioClient client) {
        this.client = client;
    }

    @Override
    public void ensureBucket(String bucket) {
        try {
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
        } catch (Exception exception) {
            throw storageFailure("unable to ensure object storage bucket", exception);
        }
    }

    @Override
    public void put(String bucket, String key, InputStream input, long size, String contentType) {
        try {
            client.putObject(
                    PutObjectArgs.builder().bucket(bucket).object(key).stream(input, size, -1)
                            .contentType(contentType)
                            .build());
        } catch (Exception exception) {
            throw storageFailure("unable to put object " + key, exception);
        }
    }

    @Override
    public void delete(String bucket, String key) {
        try {
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception exception) {
            throw storageFailure("unable to delete object " + key, exception);
        }
    }

    @Override
    public String presignedGet(String bucket, String key, Duration expiry) {
        try {
            return client.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucket)
                            .object(key)
                            .expiry(Math.toIntExact(expiry.toSeconds()))
                            .build());
        } catch (Exception exception) {
            throw storageFailure("unable to create object download URL", exception);
        }
    }

    private IllegalStateException storageFailure(String message, Exception cause) {
        return new IllegalStateException(message, cause);
    }
}
