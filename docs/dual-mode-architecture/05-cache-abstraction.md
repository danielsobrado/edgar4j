# Phase 5: Cache Abstraction

## Objective

Replace hard Redis dependency with a profile-driven cache strategy: **Caffeine** (in-memory) for low-resource mode, **Redis** for high-resource mode. Ensure all `@Cacheable`, `@CacheEvict`, and `@CachePut` annotations work transparently in both modes.

## Current Cache Usage Audit

| Service | Cache Name | TTL | Operations |
|---------|-----------|-----|-----------|
| MarketDataService | `stock-prices` | 15 min | `@Cacheable` on getStockPrice |
| MarketDataService | `company-profiles` | 24 hr | `@Cacheable` on getCompanyProfile |
| MarketDataService | `financial-metrics` | 6 hr | `@Cacheable` on getFinancialMetrics |
| MarketDataService | `historical-prices` | 1 hr | `@Cacheable` on getHistoricalPrices |
| CompanyService | `companies` | 1 hr | `@Cacheable` on findByCik |
| SettingsService | `settings` | - | `@Cacheable` / `@CacheEvict` |

## Implementation

### Spring Cache Abstraction (Already Used)

The application already uses Spring's `@Cacheable` annotation, which is backend-agnostic. The only change needed is swapping the `CacheManager` bean.

### Low-Resource Mode: Caffeine Cache Manager

```java
@Configuration
@Profile("resource-low")
public class LowResourceCacheConfig {

    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // Per-cache TTL configuration
        Map<String, String> cacheSpecs = Map.of(
            "stock-prices",      "maximumSize=5000,expireAfterWrite=900s",
            "company-profiles",  "maximumSize=2000,expireAfterWrite=86400s",
            "financial-metrics", "maximumSize=2000,expireAfterWrite=21600s",
            "historical-prices", "maximumSize=1000,expireAfterWrite=3600s",
            "companies",         "maximumSize=5000,expireAfterWrite=3600s",
            "settings",          "maximumSize=100,expireAfterWrite=86400s"
        );

        // Register caches with individual specs
        cacheSpecs.forEach((name, spec) -> {
            manager.registerCustomCache(name,
                Caffeine.from(spec).build());
        });

        // Default spec for any unregistered cache
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofHours(1)));

        return manager;
    }
}
```

### High-Resource Mode: Redis Cache Manager (Existing)

No changes needed — the existing Redis cache configuration continues to work under `resource-high` profile:

```java
@Configuration
@Profile("resource-high")
public class HighResourceCacheConfig {
    // Existing Redis cache configuration, moved here with profile annotation
}
```

### Optional: Disk-Backed Cache for Low Mode

For users who want cache persistence across restarts in low mode, offer an optional disk-backed Caffeine extension:

```yaml
edgar4j:
  storage:
    file:
      cache:
        persist-to-disk: false  # Set true to write cache snapshots on shutdown
        snapshot-path: ${edgar4j.storage.file.base-path}/cache
```

## Changes Required

1. **Move Redis cache config** to `@Profile("resource-high")` class
2. **Add Caffeine cache config** with `@Profile("resource-low")`
3. **Remove `spring.cache.type: redis`** from shared `application.yml` (move to `application-resource-high.yml`)
4. **Add `spring.cache.type: caffeine`** to `application-resource-low.yml`
5. **Verify** all `@Cacheable` annotations use string cache names (not Redis-specific features)

## Redis-Specific Code Audit

Check for any direct `RedisTemplate` or `StringRedisTemplate` usage that bypasses Spring Cache abstraction. These would need conditional alternatives.

```
# Search for direct Redis usage
grep -r "RedisTemplate\|StringRedisTemplate\|RedisConnection" src/main/java/
```

If any direct Redis usage exists, wrap it in a `CacheOperations` interface with Redis and in-memory implementations.

## Validation Checklist

- [ ] All `@Cacheable` operations work in both modes
- [ ] Cache eviction works in both modes
- [ ] No `RedisConnectionFailureException` in low mode
- [ ] Cache hit rates are logged (via Actuator metrics)
- [ ] Memory usage with Caffeine stays within bounds (max-size configured)

## Estimated Effort

- **Cache config refactoring**: 0.5 day
- **Direct Redis usage audit and fixes**: 0.5 day
- **Testing**: 0.5 day
- **Total**: 1-1.5 days
