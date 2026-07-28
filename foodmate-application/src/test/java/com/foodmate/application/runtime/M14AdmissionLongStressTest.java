package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 在真实 Redis 上运行可配置的本地长压测试，输出准入延迟分位数和容量事实。
 * 该测试默认关闭，避免普通单元测试意外产生长时间负载。
 */
@EnabledIfSystemProperty(named = "foodmate.redis-stress", matches = "true")
class M14AdmissionLongStressTest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory("localhost", 6380);
        factory.setPassword("foodmate-redis-change-me");
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        clearCoordinationKeys();
    }

    @AfterEach
    void tearDown() {
        clearCoordinationKeys();
        factory.destroy();
    }

    @Test
    void sustainedAdmissionReportsPercentilesAndNoGlobalLimitViolation() throws Exception {
        int seconds = Integer.getInteger("foodmate.stress.seconds", 30);
        int workers = Integer.getInteger("foodmate.stress.workers", 32);
        int users = Integer.getInteger("foodmate.stress.users", 24);
        assertTrue(seconds > 0 && workers > 0 && users > 0);

        AgentAdmissionService instanceA = service();
        AgentAdmissionService instanceB = service();
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        long endNanos = System.nanoTime() + Duration.ofSeconds(seconds).toNanos();
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        ConcurrentHashMap<String, String> owned = new ConcurrentHashMap<>();
        AtomicInteger submitted = new AtomicInteger();
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maxActive = new AtomicInteger();
        AtomicInteger queued = new AtomicInteger();
        AtomicInteger capacityRejected = new AtomicInteger();
        AtomicInteger coordinationErrors = new AtomicInteger();
        AtomicLong lastCompletedAt = new AtomicLong(System.nanoTime());
        LongAdder operations = new LongAdder();

        for (int worker = 0; worker < workers; worker++) {
            final int workerId = worker;
            executor.submit(() -> {
                ready.countDown();
                try {
                    start.await();
                    while (System.nanoTime() < endNanos) {
                        String runId = "m14-stress-" + workerId + "-" + submitted.incrementAndGet();
                        long userId = 10000L + (workerId % users);
                        long sessionId = userId * 100 + (workerId % 2);
                        long started = System.nanoTime();
                        try {
                            AgentAdmissionService service = (workerId & 1) == 0 ? instanceA : instanceB;
                            AgentAdmissionService.Admission admission = service.admit(runId, userId, sessionId);
                            latencies.add(TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - started));
                            operations.increment();
                            if (admission.state() == AgentAdmissionService.State.ACTIVE) {
                                owned.put(runId, workerId % 2 == 0 ? "A" : "B");
                                int current = active.incrementAndGet();
                                maxActive.accumulateAndGet(current, Math::max);
                                for (String promoted : admission.promotedRunIds()) {
                                    if (owned.remove(promoted) != null) {
                                        active.decrementAndGet();
                                        service.releaseAndPromote(promoted);
                                    }
                                }
                                Thread.yield();
                                if (owned.remove(runId) != null) {
                                    active.decrementAndGet();
                                    service.releaseAndPromote(runId);
                                    lastCompletedAt.set(System.nanoTime());
                                }
                            } else {
                                queued.incrementAndGet();
                            }
                        } catch (com.foodmate.shared.runtime.RuntimeException exception) {
                            if ("RUNTIME_CAPACITY_EXCEEDED".equals(exception.code())) {
                                capacityRejected.incrementAndGet();
                            } else if ("RUNTIME_COORDINATION_UNAVAILABLE".equals(exception.code())) {
                                coordinationErrors.incrementAndGet();
                            } else {
                                throw exception;
                            }
                        }
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        executor.shutdown();
        assertTrue(executor.awaitTermination(seconds + 15L, TimeUnit.SECONDS));

        // 将仍在队列中的 permit 清掉，避免测试之间共享脏状态。
        for (String runId : new ArrayList<>(owned.keySet())) {
            instanceA.releaseAndPromote(runId);
            instanceB.releaseAndPromote(runId);
        }
        assertTrue(maxActive.get() <= 20, "Redis global active limit exceeded: " + maxActive.get());
        assertTrue(coordinationErrors.get() == 0, "Redis coordination errors: " + coordinationErrors.get());

        List<Long> ordered = new ArrayList<>(latencies);
        Collections.sort(ordered);
        System.out.printf(
                "M14_STRESS seconds=%d workers=%d operations=%d active_max=%d queued=%d capacity_rejected=%d p50_us=%d p95_us=%d p99_us=%d last_completion_age_ms=%d%n",
                seconds, workers, operations.sum(), maxActive.get(), queued.get(), capacityRejected.get(),
                percentile(ordered, 0.50), percentile(ordered, 0.95), percentile(ordered, 0.99),
                TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastCompletedAt.get()));
    }

    private AgentAdmissionService service() {
        ObjectProvider<StringRedisTemplate> provider = new ObjectProvider<>() {
            @Override public StringRedisTemplate getIfAvailable() { return redis; }
            @Override public StringRedisTemplate getIfUnique() { return redis; }
            @Override public StringRedisTemplate getIfAvailable(java.util.function.Supplier<StringRedisTemplate> s) { return redis; }
            @Override public StringRedisTemplate getIfUnique(java.util.function.Supplier<StringRedisTemplate> s) { return redis; }
            @Override public StringRedisTemplate getObject(Object... args) { return redis; }
            @Override public StringRedisTemplate getObject() { return redis; }
        };
        return new AgentAdmissionService(provider, true, 20, 2, 100, 30, 3600);
    }

    private void clearCoordinationKeys() {
        var keys = redis.keys("foodmate:agent:admission:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    private static long percentile(List<Long> values, double fraction) {
        if (values.isEmpty()) return 0;
        int index = Math.min(values.size() - 1, (int) Math.ceil(values.size() * fraction) - 1);
        return values.get(Math.max(0, index));
    }
}
