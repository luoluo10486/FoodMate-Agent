package com.foodmate.infrastructure.coordination.redis;

import com.foodmate.application.runtime.admission.port.out.AdmissionCoordinationPort;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

/** Redis implementation of distributed agent admission coordination. */
@Component
public final class RedisAdmissionCoordinationAdapter implements AdmissionCoordinationPort {
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
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now)
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
              redis.call('PEXPIRE', KEYS[3], tonumber(ARGV[5]) * 1000)
              return 'active'
            end
            if redis.call('ZCARD', KEYS[4]) >= queue_limit then return 'capacity' end
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
            redis.call('PEXPIRE', KEYS[3], tonumber(ARGV[4]) * 1000)
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

    public RedisAdmissionCoordinationAdapter(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public AdmissionCoordinationPort.AcquireResult acquire(
            AdmissionCoordinationPort.AcquireRequest request) {
        try {
            String result =
                    redis.execute(
                            ACQUIRE,
                            List.of(
                                    GLOBAL_KEY,
                                    userKey(request.userId()),
                                    permitKey(request.runId()),
                                    QUEUE_KEY),
                            request.runId(),
                            Long.toString(request.sessionId()),
                            Long.toString(request.userId()),
                            Long.toString(request.now().toEpochMilli() / 1000),
                            Long.toString(request.lease().toSeconds()),
                            Integer.toString(request.globalLimit()),
                            Integer.toString(request.userLimit()),
                            Integer.toString(request.queueLimit()),
                            Long.toString(request.queueLease().toMillis()),
                            Integer.toString(request.priority()));
            return new AdmissionCoordinationPort.AcquireResult(
                    switch (result) {
                        case "active" -> AdmissionCoordinationPort.AcquireResult.State.ACTIVE;
                        case "queued" -> AdmissionCoordinationPort.AcquireResult.State.QUEUED;
                        case "capacity" -> AdmissionCoordinationPort.AcquireResult.State.CAPACITY;
                        default -> null;
                    });
        } catch (RuntimeException exception) {
            throw coordinationFailure(exception);
        }
    }

    @Override
    public List<String> releaseAndPromote(AdmissionCoordinationPort.ReleaseRequest request) {
        try {
            Map<Object, Object> current = redis.opsForHash().entries(permitKey(request.runId()));
            Object user = current.get("user_id");
            if (user == null) return List.of();
            Long released =
                    redis.execute(
                            RELEASE,
                            List.of(
                                    GLOBAL_KEY,
                                    userKey(Long.parseLong(user.toString())),
                                    permitKey(request.runId()),
                                    QUEUE_KEY),
                            request.runId());
            if (released == null || released == 0) return List.of();

            List<String> promoted = new ArrayList<>();
            for (String queuedRun :
                    redis.opsForZSet().range(QUEUE_KEY, 0, request.maxCandidates() - 1)) {
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
                                Long.toString(request.now().toEpochMilli() / 1000),
                                Integer.toString(request.globalLimit()),
                                Integer.toString(request.userLimit()),
                                Long.toString(request.lease().toSeconds()),
                                queuedRun,
                                session.toString());
                if ("active".equals(result)) promoted.add(queuedRun);
            }
            return promoted;
        } catch (RuntimeException exception) {
            throw coordinationFailure(exception);
        }
    }

    @Override
    public void renewActiveLeases(AdmissionCoordinationPort.RenewRequest request) {
        try {
            long nowSeconds = request.now().toEpochMilli() / 1000;
            for (String runId : redis.opsForZSet().range(GLOBAL_KEY, 0, -1)) {
                Map<Object, Object> permit = redis.opsForHash().entries(permitKey(runId));
                Object user = permit.get("user_id");
                if (user == null) continue;
                redis.execute(
                        RENEW,
                        List.of(
                                GLOBAL_KEY,
                                userKey(Long.parseLong(user.toString())),
                                permitKey(runId)),
                        runId,
                        Long.toString(nowSeconds),
                        Long.toString(request.lease().toSeconds()),
                        Long.toString(request.lease().toMillis()));
            }
        } catch (RuntimeException exception) {
            throw coordinationFailure(exception);
        }
    }

    @Override
    public boolean isActive(String runId) {
        try {
            Object state = redis.opsForHash().get(permitKey(runId), "state");
            return "active".equals(state == null ? null : state.toString());
        } catch (RuntimeException exception) {
            throw coordinationFailure(exception);
        }
    }

    @Override
    public void requireAvailable() {
        if (redis == null) throw coordinationUnavailable();
    }

    private String userKey(long userId) {
        return USER_PREFIX + userId;
    }

    private String permitKey(String runId) {
        return PERMIT_PREFIX + runId;
    }

    private com.foodmate.shared.runtime.RuntimeException coordinationFailure(
            RuntimeException exception) {
        if (exception instanceof com.foodmate.shared.runtime.RuntimeException runtimeException)
            return runtimeException;
        return coordinationUnavailable();
    }

    private com.foodmate.shared.runtime.RuntimeException coordinationUnavailable() {
        return new com.foodmate.shared.runtime.RuntimeException(
                "RUNTIME_COORDINATION_UNAVAILABLE", "Redis admission coordination is unavailable");
    }
}
