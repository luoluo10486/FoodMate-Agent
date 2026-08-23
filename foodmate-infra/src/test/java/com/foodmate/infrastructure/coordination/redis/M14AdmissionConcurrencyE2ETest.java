package com.foodmate.infrastructure.coordination.redis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.foodmate.application.runtime.admission.AgentAdmissionService;
import com.foodmate.application.runtime.admission.impl.AgentAdmissionServiceImpl;
import com.foodmate.application.runtime.admission.port.out.AdmissionCoordinationPort;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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

/** Validates admission limits, promotion, and lease recovery against shared Redis. */
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
        factory.setDatabase(15);
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
    void userLimitCountsRunsWhenRunsShareOneSession() {
        AgentAdmissionService service = service(3, 2, 2, 30, 5);
        assertEquals(
                AgentAdmissionService.State.ACTIVE,
                service.admit("m14-same-session-1", 9150, 150).state());
        assertEquals(
                AgentAdmissionService.State.ACTIVE,
                service.admit("m14-same-session-2", 9150, 150).state());
        assertEquals(
                AgentAdmissionService.State.QUEUED,
                service.admit("m14-same-session-3", 9150, 150).state());
        assertEquals(2L, redis.opsForZSet().zCard("foodmate:agent:admission:active:user:9150"));
    }

    @Test
    void expiredLeaseDoesNotKeepUserSlotOccupied() throws InterruptedException {
        AgentAdmissionService shortLeaseA = service(100, 1, 2, 1, 5);
        AgentAdmissionService shortLeaseB = service(100, 1, 2, 1, 5);
        String expiredRun = "m14-expired-" + UUID.randomUUID();
        long expiredUser = 920100000L + Math.abs(System.nanoTime() % 100000L);
        assertEquals(
                AgentAdmissionService.State.ACTIVE,
                shortLeaseA.admit(expiredRun, expiredUser, 21).state());
        Thread.sleep(Duration.ofMillis(2200));
        assertEquals(
                AgentAdmissionService.State.ACTIVE,
                shortLeaseB.admit("m14-renewed-" + UUID.randomUUID(), expiredUser, 22).state());
    }

    @Test
    void expiredLeaseCannotBeRenewedOrReportedActive() throws InterruptedException {
        AgentAdmissionService shortLease = service(1, 1, 2, 1, 5);
        String runId = "m14-renew-expired-" + UUID.randomUUID();
        assertEquals(AgentAdmissionService.State.ACTIVE, shortLease.admit(runId, 9250, 25).state());
        Thread.sleep(2200);
        shortLease.renewActiveLeases();
        assertFalse(shortLease.isActive(runId));
        assertEquals(
                AgentAdmissionService.State.ACTIVE,
                shortLease.admit("m14-renewed-after-expiry", 9251, 26).state());
    }

    @Test
    void expiredRunCanReacquireItsPermitBeforeRedisHashExpiry() throws InterruptedException {
        AgentAdmissionService shortLease = service(1, 1, 1, 1, 5);
        String runId = "m14-reacquire-expired-" + UUID.randomUUID();
        assertEquals(AgentAdmissionService.State.ACTIVE, shortLease.admit(runId, 9260, 27).state());
        Thread.sleep(2200);
        assertEquals(AgentAdmissionService.State.ACTIVE, shortLease.admit(runId, 9260, 27).state());
        assertEquals(
                AgentAdmissionService.State.QUEUED,
                shortLease.admit("m14-reacquire-competitor", 9261, 28).state());
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
            ObjectProvider<AdmissionCoordinationPort> provider,
            int global,
            int user,
            int queue,
            int lease,
            int queueLease) {
        return new AgentAdmissionServiceImpl(
                provider, true, global, user, queue, lease, queueLease);
    }

    private void clearCoordinationKeys() {
        var keys = redis.keys("foodmate:agent:admission:*");
        if (keys != null && !keys.isEmpty()) redis.delete(keys);
    }

    private static ObjectProvider<AdmissionCoordinationPort> provider(StringRedisTemplate value) {
        AdmissionCoordinationPort port = new RedisAdmissionCoordinationAdapter(value);
        return new ObjectProvider<>() {
            public AdmissionCoordinationPort getObject(Object... args) {
                return port;
            }

            public AdmissionCoordinationPort getIfAvailable() {
                return port;
            }

            public AdmissionCoordinationPort getIfUnique() {
                return port;
            }

            public Stream<AdmissionCoordinationPort> orderedStream() {
                return Stream.of(port);
            }

            public Stream<AdmissionCoordinationPort> stream() {
                return Stream.of(port);
            }
        };
    }
}
