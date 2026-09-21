package com.wealthtech.rebalance.common.lock;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis-based mutual exclusion (SET NX PX + a token-checked Lua compare-and-delete on unlock, so
 * a service never releases a lock it doesn't hold -- e.g. after its own TTL already expired and
 * someone else acquired it). Used by drift-engine-service to guarantee two instances (or a
 * redelivered message racing the original) never run the rebalance transaction for the same
 * account concurrently. This is a second, faster layer on top of the JPA {@code @Version}
 * optimistic lock in account-service -- the lock avoids wasted work and lock-contention noise,
 * while the DB version column remains the ultimate correctness guarantee even if the lock is
 * lost (Redis failover, TTL race).
 */
@Service
public class DistributedLockService {

    private static final String UNLOCK_SCRIPT =
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> unlockScript;

    public DistributedLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.unlockScript = new DefaultRedisScript<>(UNLOCK_SCRIPT, Long.class);
    }

    public record Lock(String key, String token) {
    }

    public Optional<Lock> tryLock(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(acquired) ? Optional.of(new Lock(key, token)) : Optional.empty();
    }

    public void unlock(Lock lock) {
        redisTemplate.execute(unlockScript, Collections.singletonList(lock.key()), lock.token());
    }
}
