package com.demo.orderplatform.inventory.service;

import com.demo.orderplatform.inventory.domain.StockItem;
import com.demo.orderplatform.inventory.store.InventoryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Read-through cache for the catalog. Every order placed does a price lookup per
 * line, and prices barely move, so this keeps the busiest read path off Postgres.
 * The cache is only ever consulted for reads — reservations always go to the
 * database, because a cached stock count is a guess and a decrement is not.
 */
@Service
public class StockCatalog {

    private static final Logger log = LoggerFactory.getLogger(StockCatalog.class);
    private static final String PREFIX = "inv:sku:";

    private final InventoryRepository repository;
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public StockCatalog(InventoryRepository repository,
                        StringRedisTemplate redis,
                        ObjectMapper objectMapper,
                        @Value("${app.cache.ttl-seconds}") int ttlSeconds) {
        this.repository = repository;
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(ttlSeconds);
    }

    public Optional<StockItem> lookup(String sku) {
        StockItem cached = readCache(sku);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<StockItem> item = repository.find(sku);
        item.ifPresent(this::writeCache);
        return item;
    }

    public List<StockItem> all() {
        return repository.findAll();
    }

    /** Called after a reservation or release commits. */
    public void evict(Collection<String> skus) {
        if (skus.isEmpty()) {
            return;
        }
        try {
            redis.delete(skus.stream().map(sku -> PREFIX + sku).toList());
        } catch (RuntimeException e) {
            // A stale entry expires on its own within the TTL; losing Redis is
            // not a reason to fail an order that already committed.
            log.warn("could not evict {} from cache", skus, e);
        }
    }

    private StockItem readCache(String sku) {
        try {
            String json = redis.opsForValue().get(PREFIX + sku);
            return json == null ? null : objectMapper.readValue(json, StockItem.class);
        } catch (Exception e) {
            log.warn("cache read failed for {}, falling back to postgres", sku, e);
            return null;
        }
    }

    private void writeCache(StockItem item) {
        try {
            redis.opsForValue().set(PREFIX + item.sku(), objectMapper.writeValueAsString(item), ttl);
        } catch (Exception e) {
            log.warn("cache write failed for {}", item.sku(), e);
        }
    }
}
