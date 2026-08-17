package com.demo.orderplatform.order.service;

import java.time.Duration;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * A retrying client — a mobile app on a flaky connection, a load balancer that
 * gave up early — will happily POST the same order twice. The first request to
 * claim the key wins via Redis SETNX; every later one is handed back the order
 * id the first one created instead of creating a second order.
 */
@Component
public class IdempotencyKeys {

    private static final String PREFIX = "idem:order:";

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public IdempotencyKeys(StringRedisTemplate redis,
                           @Value("${app.idempotency.ttl-hours}") int ttlHours) {
        this.redis = redis;
        this.ttl = Duration.ofHours(ttlHours);
    }

    /**
     * @return empty if this caller claimed the key and should do the work;
     *         otherwise the order id the original caller was given
     */
    public Optional<String> claim(String key, String orderId) {
        Boolean claimed = redis.opsForValue().setIfAbsent(PREFIX + key, orderId, ttl);
        if (Boolean.TRUE.equals(claimed)) {
            return Optional.empty();
        }
        return Optional.ofNullable(redis.opsForValue().get(PREFIX + key));
    }

    /** Hand the key back if the write it was guarding never landed. */
    public void release(String key) {
        redis.delete(PREFIX + key);
    }
}
