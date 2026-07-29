package com.foodmate.application.runtime;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/**
 * Agent 运行准入的 Redis 协调层。
 *
 * <p>这里不把 Redis 当业务权威：PostgreSQL 保存 Run 和 Outbox，Redis 只保存 短期 lease。Lua
 * 脚本把清理过期租约、检查容量和写入租约放进一次原子操作， 避免多个 Java 实例之间重复放行。
 */
@Service
public class AgentAdmissionService {
    private static final String PREFIX = "foodmate:agent:admission:";
    private static final String GLOBAL_KEY = PREFIX + "active:global";
    private static final String QUEUE_KEY = PREFIX + "queue";
    private static final String USER_PREFIX = PREFIX + "active:user:";
    private static final String PERMIT_PREFIX = PREFIX + "permit:";

    private static final DefaultRedisScript<String> ACQUIRE =
            new DefaultRedisScript<>(
                    """
            local now = tonumber(ARGV[4])
            local lease_until = now + tonumber(ARGV[5])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
            -- 全局集合过期清理不能替代用户集合清理；否则用户旧 lease 会永久占用用户并发额度。
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now)
            -- Queue score is an ordering score, not an expiry timestamp. Remove only
            -- entries whose permit hash expired, otherwise normal FIFO entries vanish.
            for _, queued_run in ipairs(redis.call('ZRANGE', KEYS[4], 0, -1)) do
              if redis.call('EXISTS', 'foodmate:agent:admission:permit:' .. queued_run) == 0 then
                redis.call('ZREM', KEYS[4], queued_run)
              end
            end
            local existing = redis.call('HGET', KEYS[3], 'state')
            if existing == 'active' or existing == 'queued' then return existing end
            local global_limit = tonumber(ARGV[6])
            local user_limit = tonumber(ARGV[7])
            local queue_limit = tonumber(ARGV[8])
            local user_active = redis.call('ZCARD', KEYS[2])
            local global_active = redis.call('ZCARD', KEYS[1])
            if global_active < global_limit and user_active < user_limit then
              redis.call('ZADD', KEYS[1], lease_until, ARGV[1])
              redis.call('ZADD', KEYS[2], lease_until, ARGV[2])
              redis.call('HSET', KEYS[3], 'state', 'active', 'user_id', ARGV[3], 'session_id', ARGV[2], 'run_id', ARGV[1])
              redis.call('PEXPIRE', KEYS[3], tonumber(ARGV[5]))
              return 'active'
            end
            if redis.call('ZCARD', KEYS[4]) >= queue_limit then return 'capacity' end
            -- 分数同时保留提交时间和有限 priority；老任务会自然先于后续同优先级任务，避免无限插队。
            redis.call('ZADD', KEYS[4], now - (tonumber(ARGV[10]) * 5), ARGV[1])
            redis.call('HSET', KEYS[3], 'state', 'queued', 'user_id', ARGV[3], 'session_id', ARGV[2], 'run_id', ARGV[1])
            redis.call('PEXPIRE', KEYS[3], tonumber(ARGV[9]))
            return 'queued'
            """,
                    String.class);

    private static final DefaultRedisScript<String> PROMOTE =
            new DefaultRedisScript<>(
                    """
            local now = tonumber(ARGV[1])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now)
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now)
            for _, queued_run in ipairs(redis.call('ZRANGE', KEYS[4], 0, -1)) do
              if redis.call('EXISTS', 'foodmate:agent:admission:permit:' .. queued_run) == 0 then
                redis.call('ZREM', KEYS[4], queued_run)
              end
            end
            local state = redis.call('HGET', KEYS[3], 'state')
            if state ~= 'queued' then return 'none' end
            if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[2]) then return 'waiting' end
            local user_key = KEYS[2]
            if redis.call('ZCARD', user_key) >= tonumber(ARGV[3]) then return 'waiting' end
            local lease_until = now + tonumber(ARGV[4])
            redis.call('ZREM', KEYS[4], ARGV[5])
            redis.call('ZADD', KEYS[1], lease_until, ARGV[5])
            redis.call('ZADD', user_key, lease_until, ARGV[6])
            redis.call('HSET', KEYS[3], 'state', 'active')
            redis.call('PEXPIRE', KEYS[3], tonumber(ARGV[4]))
            return 'active'
            """,
                    String.class);

    private static final DefaultRedisScript<Long> RELEASE =
            new DefaultRedisScript<>(
                    """
            local state = redis.call('HGET', KEYS[3], 'state')
            if not state then return 0 end
            redis.call('ZREM', KEYS[1], ARGV[1])
            redis.call('ZREM', KEYS[2], redis.call('HGET', KEYS[3], 'session_id'))
            redis.call('ZREM', KEYS[4], ARGV[1])
            redis.call('DEL', KEYS[3])
            return 1
            """,
                    Long.class);
    private static final DefaultRedisScript<Long> RENEW =
            new DefaultRedisScript<>(
                    """
            local state = redis.call('HGET', KEYS[3], 'state')
            if state ~= 'active' then return 0 end
            local lease_until = tonumber(ARGV[2]) + tonumber(ARGV[3])
            redis.call('ZADD', KEYS[1], lease_until, ARGV[1])
            redis.call('ZADD', KEYS[2], lease_until, redis.call('HGET', KEYS[3], 'session_id'))
            redis.call('PEXPIRE', KEYS[3], tonumber(ARGV[4]))
            return 1
            """,
                    Long.class);

    private final StringRedisTemplate redis;
    private final boolean enabled;
    private final int globalLimit;
    private final int userLimit;
    private final int queueLimit;
    private final Duration lease;
    private final Duration queueLease;

    public AgentAdmissionService(
            ObjectProvider<StringRedisTemplate> provider,
            @Value(
                            "${foodmate.runtime.admission.enabled:${FOODMATE_AGENT_ADMISSION_ENABLED:false}}")
                    boolean enabled,
            @Value("${FOODMATE_AGENT_MAX_ACTIVE_RUNS_GLOBAL:20}") int globalLimit,
            @Value("${FOODMATE_AGENT_MAX_ACTIVE_RUNS_PER_USER:2}") int userLimit,
            @Value("${FOODMATE_AGENT_MAX_QUEUED_RUNS_GLOBAL:100}") int queueLimit,
            @Value("${FOODMATE_AGENT_PERMIT_LEASE_SECONDS:180}") int leaseSeconds,
            @Value("${FOODMATE_AGENT_QUEUE_LEASE_SECONDS:3600}") int queueLeaseSeconds) {
        this.redis = provider.getIfAvailable();
        this.enabled = enabled;
        this.globalLimit = positive(globalLimit, "globalLimit");
        this.userLimit = positive(userLimit, "userLimit");
        this.queueLimit = positive(queueLimit, "queueLimit");
        this.lease = Duration.ofSeconds(positive(leaseSeconds, "leaseSeconds"));
        this.queueLease = Duration.ofSeconds(positive(queueLeaseSeconds, "queueLeaseSeconds"));
    }

    public Admission admit(String runId, long userId, long sessionId) {
        return admit(runId, userId, sessionId, 0);
    }

    public Admission admit(String runId, long userId, long sessionId, int priority) {
        if (!enabled) return new Admission(State.ACTIVE, List.of());
        requireRedis();
        String result;
        try {
            result =
                    redis.execute(
                            ACQUIRE,
                            List.of(GLOBAL_KEY, userKey(userId), permitKey(runId), QUEUE_KEY),
                            runId,
                            Long.toString(sessionId),
                            Long.toString(userId),
                            Long.toString(System.currentTimeMillis() / 1000),
                            Long.toString(lease.toMillis() / 1000),
                            Integer.toString(globalLimit),
                            Integer.toString(userLimit),
                            Integer.toString(queueLimit),
                            Long.toString(queueLease.toMillis()),
                            Integer.toString(Math.max(0, priority)));
        } catch (RuntimeException exception) {
            throw coordinationFailure(exception);
        }
        return switch (result == null ? "unavailable" : result) {
            case "active" -> new Admission(State.ACTIVE, List.of());
            case "queued" -> new Admission(State.QUEUED, List.of());
            case "capacity" -> throw runtime("RUNTIME_CAPACITY_EXCEEDED", "Agent 队列已满");
            default -> throw runtime("RUNTIME_COORDINATION_UNAVAILABLE", "Agent 准入协调失败");
        };
    }

    /** 终态释放 lease，并按队列顺序尝试提升等待中的 Run。 */
    public List<String> releaseAndPromote(String runId) {
        if (!enabled) return List.of();
        requireRedis();
        Map<Object, Object> current;
        try {
            current = redis.opsForHash().entries(permitKey(runId));
        } catch (RuntimeException exception) {
            throw coordinationFailure(exception);
        }
        Object user = current.get("user_id");
        if (user == null) return List.of();
        Long released;
        try {
            released =
                    redis.execute(
                            RELEASE,
                            List.of(
                                    GLOBAL_KEY,
                                    userKey(Long.parseLong(user.toString())),
                                    permitKey(runId),
                                    QUEUE_KEY),
                            runId);
        } catch (RuntimeException exception) {
            throw coordinationFailure(exception);
        }
        if (released == null || released == 0) return List.of();
        List<String> promoted = new ArrayList<>();
        for (String queuedRun : redis.opsForZSet().range(QUEUE_KEY, 0, 9)) {
            Map<Object, Object> permit = redis.opsForHash().entries(permitKey(queuedRun));
            Object queuedUser = permit.get("user_id");
            Object session = permit.get("session_id");
            if (queuedUser == null || session == null) continue;
            String result =
                    redis.execute(
                            PROMOTE,
                            List.of(
                                    GLOBAL_KEY,
                                    userKey(Long.parseLong(queuedUser.toString())),
                                    permitKey(queuedRun),
                                    QUEUE_KEY),
                            Long.toString(System.currentTimeMillis() / 1000),
                            Integer.toString(globalLimit),
                            Integer.toString(userLimit),
                            Long.toString(lease.toMillis() / 1000),
                            queuedRun,
                            session.toString());
            if ("active".equals(result)) promoted.add(queuedRun);
        }
        return promoted;
    }

    /** 续租由 Redis active 集合驱动，不能依赖某一个 Java 进程内存。 */
    public void renewActiveLeases() {
        if (!enabled) return;
        requireRedis();
        long nowSeconds = System.currentTimeMillis() / 1000;
        for (String runId : redis.opsForZSet().range(GLOBAL_KEY, 0, -1)) {
            Map<Object, Object> permit = redis.opsForHash().entries(permitKey(runId));
            Object user = permit.get("user_id");
            if (user == null) continue;
            redis.execute(
                    RENEW,
                    List.of(GLOBAL_KEY, userKey(Long.parseLong(user.toString())), permitKey(runId)),
                    runId,
                    Long.toString(nowSeconds),
                    Long.toString(lease.toMillis() / 1000),
                    Long.toString(lease.toMillis()));
        }
    }

    public void requireRedis() {
        if (redis == null) throw runtime("RUNTIME_COORDINATION_UNAVAILABLE", "Redis 准入协调不可用");
    }

    private com.foodmate.shared.runtime.RuntimeException coordinationFailure(
            RuntimeException exception) {
        if (exception instanceof com.foodmate.shared.runtime.RuntimeException runtimeException)
            return runtimeException;
        return runtime("RUNTIME_COORDINATION_UNAVAILABLE", "Redis 准入协调不可用");
    }

    private String userKey(long userId) {
        return USER_PREFIX + userId;
    }

    private String permitKey(String runId) {
        return PERMIT_PREFIX + runId;
    }

    private static int positive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static com.foodmate.shared.runtime.RuntimeException runtime(
            String code, String message) {
        return new com.foodmate.shared.runtime.RuntimeException(code, message);
    }

    public enum State {
        ACTIVE,
        QUEUED
    }

    public record Admission(State state, List<String> promotedRunIds) {}
}
