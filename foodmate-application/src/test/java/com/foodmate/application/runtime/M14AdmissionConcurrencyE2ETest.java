package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** 两个独立 Java 准入服务共享真实 Redis 时的上限、晋升和租约回收验证。 */
@EnabledIfSystemProperty(named = "foodmate.redis-e2e", matches = "true")
class M14AdmissionConcurrencyE2ETest {
    private LettuceConnectionFactory factory;
    private StringRedisTemplate redis;
    private AgentAdmissionService instanceA;
    private AgentAdmissionService instanceB;

    @BeforeEach
    void setUp() {
        factory = new LettuceConnectionFactory("localhost", 6380);
        factory.setPassword("foodmate-redis-change-me");
        factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
        redis.afterPropertiesSet();
        clearCoordinationKeys();
        instanceA = service(2, 1, 2, 30, 5);
        instanceB = service(2, 1, 2, 30, 5);
    }

    @AfterEach
    void tearDown() {
        clearCoordinationKeys();
        factory.destroy();
    }

    @Test
    void sharedRedisEnforcesGlobalUserQueueAndPromotionAcrossInstances() {
        assertEquals(
                AgentAdmissionService.State.ACTIVE, instanceA.admit("m14-a-1", 9101, 1).state());
        assertEquals(
                AgentAdmissionService.State.ACTIVE, instanceB.admit("m14-b-1", 9102, 2).state());
        assertEquals(
                AgentAdmissionService.State.QUEUED, instanceA.admit("m14-a-2", 9103, 3).state());
        assertEquals(
                AgentAdmissionService.State.QUEUED, instanceB.admit("m14-b-2", 9104, 4).state());
        assertThrows(
                com.foodmate.shared.runtime.RuntimeException.class,
                () -> instanceA.admit("m14-capacity", 9105, 5));

        List<String> promoted = instanceB.releaseAndPromote("m14-b-1");
        assertEquals(List.of("m14-a-2"), promoted);
        assertEquals(2L, redis.opsForZSet().zCard("foodmate:agent:admission:active:global"));
    }

    @Test
    void expiredLeaseDoesNotKeepUserSlotOccupied() throws InterruptedException {
        AgentAdmissionService shortLeaseA = service(2, 1, 2, 1, 5);
        AgentAdmissionService shortLeaseB = service(2, 1, 2, 1, 5);
        assertEquals(
                AgentAdmissionService.State.ACTIVE,
                shortLeaseA.admit("m14-expired", 9201, 21).state());
        Thread.sleep(Duration.ofMillis(2200));
        assertEquals(
                AgentAdmissionService.State.ACTIVE,
                shortLeaseB.admit("m14-renewed", 9201, 22).state());
    }

    @Test
    void continuationPriorityIsPromotedBeforeOlderNormalWork() {
        AgentAdmissionService priorityA = service(1, 1, 3, 30, 5);
        AgentAdmissionService priorityB = service(1, 1, 3, 30, 5);
        priorityA.admit("m14-holder", 9301, 31);
        assertEquals(
                AgentAdmissionService.State.QUEUED,
                priorityB.admit("m14-normal", 9302, 32, 0).state());
        assertEquals(
                AgentAdmissionService.State.QUEUED,
                priorityA.admit("m14-continuation", 9303, 33, 10).state());
        assertEquals(List.of("m14-continuation"), priorityB.releaseAndPromote("m14-holder"));
    }

    @Test
    void concurrentInstancesNeverExceedGlobalLimit() throws Exception {
        AgentAdmissionService first = service(3, 10, 20, 30, 5);
        AgentAdmissionService second = service(3, 10, 20, 30, 5);
        int total = 20;
        CountDownLatch ready = new CountDownLatch(total);
        CountDownLatch start = new CountDownLatch(1);
        ConcurrentLinkedQueue<String> states = new ConcurrentLinkedQueue<>();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            final int index = i;
            Thread thread =
                    new Thread(
                            () -> {
                                ready.countDown();
                                try {
                                    start.await(5, TimeUnit.SECONDS);
                                    states.add(
                                            (index % 2 == 0 ? first : second)
                                                    .admit(
                                                            "m14-race-" + index,
                                                            9400 + index,
                                                            40 + index)
                                                    .state()
                                                    .name());
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                }
                            });
            threads.add(thread);
            thread.start();
        }
        assertEquals(true, ready.await(5, TimeUnit.SECONDS));
        start.countDown();
        for (Thread thread : threads) thread.join(5000);
        assertEquals(3L, redis.opsForZSet().zCard("foodmate:agent:admission:active:global"));
        assertEquals(total, states.size());
    }

    @Test
    void queueLeaseExpiryFreesQueueCapacity() throws Exception {
        AgentAdmissionService shortQueue = service(1, 1, 1, 30, 1);
        shortQueue.admit("m14-queue-holder", 9501, 51);
        assertEquals(
                AgentAdmissionService.State.QUEUED,
                shortQueue.admit("m14-queue-expiring", 9502, 52).state());
        Thread.sleep(2200);
        assertEquals(
                AgentAdmissionService.State.QUEUED,
                shortQueue.admit("m14-queue-new", 9503, 53).state());
    }

    @Test
    void redisUnavailableIsReportedAsCoordinationFailure() {
        LettuceConnectionFactory unavailableFactory =
                new LettuceConnectionFactory("localhost", 6399);
        unavailableFactory.afterPropertiesSet();
        StringRedisTemplate unavailableRedis = new StringRedisTemplate(unavailableFactory);
        unavailableRedis.afterPropertiesSet();
        try {
            AgentAdmissionService unavailable = service(provider(unavailableRedis), 1, 1, 1, 30, 5);
            assertThrows(
                    com.foodmate.shared.runtime.RuntimeException.class,
                    () -> unavailable.admit("m14-redis-down", 9601, 61));
        } finally {
            unavailableFactory.destroy();
        }
    }

    private AgentAdmissionService service(
            int global, int user, int queue, int lease, int queueLease) {
        return service(provider(redis), global, user, queue, lease, queueLease);
    }

    private AgentAdmissionService service(
            ObjectProvider<StringRedisTemplate> provider,
            int global,
            int user,
            int queue,
            int lease,
            int queueLease) {
        return new AgentAdmissionService(provider, true, global, user, queue, lease, queueLease);
    }

    private void clearCoordinationKeys() {
        var keys = redis.keys("foodmate:agent:admission:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    private static ObjectProvider<StringRedisTemplate> provider(StringRedisTemplate value) {
        return new ObjectProvider<>() {
            public StringRedisTemplate getObject(Object... args) {
                return value;
            }

            public StringRedisTemplate getIfAvailable() {
                return value;
            }

            public StringRedisTemplate getIfUnique() {
                return value;
            }

            public Stream<StringRedisTemplate> orderedStream() {
                return Stream.of(value);
            }

            public Stream<StringRedisTemplate> stream() {
                return Stream.of(value);
            }
        };
    }
}
