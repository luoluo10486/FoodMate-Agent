package com.foodmate.shared.id;

/**
 * Snowflake ID 生成器。
 */
public class SnowflakeIdGenerator implements IdGenerator {
    private static final long CUSTOM_EPOCH = 1_704_067_200_000L;
    private static final long WORKER_ID_BITS = 10L;
    private static final long SEQUENCE_BITS = 12L;
    private static final long MAX_WORKER_ID = (1L << WORKER_ID_BITS) - 1L;
    private static final long SEQUENCE_MASK = (1L << SEQUENCE_BITS) - 1L;
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = WORKER_ID_BITS + SEQUENCE_BITS;

    private final long workerId;
    private long lastTimestamp = -1L;
    private long sequence = 0L;

    public SnowflakeIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be between 0 and " + MAX_WORKER_ID);
        }
        this.workerId = workerId;
    }

    @Override
    public synchronized long nextId() {
        long timestamp = timestamp();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards by " + (lastTimestamp - timestamp) + " ms");
        }
        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitUntilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }
        lastTimestamp = timestamp;
        return (timestamp << TIMESTAMP_SHIFT) | (workerId << WORKER_ID_SHIFT) | sequence;
    }

    private long waitUntilNextMillis(long previousTimestamp) {
        long timestamp = timestamp();
        while (timestamp <= previousTimestamp) {
            timestamp = timestamp();
        }
        return timestamp;
    }

    private long timestamp() {
        return System.currentTimeMillis() - CUSTOM_EPOCH;
    }
}
