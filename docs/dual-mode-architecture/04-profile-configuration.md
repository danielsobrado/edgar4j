# Phase 4: Profile & Auto-Configuration

## Objective

Wire everything together with Spring profiles so that a single property (`edgar4j.resource-mode`) controls which beans, data sources, and configurations are active at runtime.

## Profile Activation Strategy

### Property-Driven Profile Activation

```yaml
# application.yml
edgar4j:
  resource-mode: ${EDGAR4J_RESOURCE_MODE:high}
```

A custom `EnvironmentPostProcessor` translates the property into Spring profile activation:

```java
public class ResourceModeProfileActivator implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(
            ConfigurableEnvironment environment, SpringApplication application) {

        String mode = environment.getProperty("edgar4j.resource-mode", "high");

        if ("low".equalsIgnoreCase(mode)) {
            environment.addActiveProfile("resource-low");
        } else {
            environment.addActiveProfile("resource-high");
        }
    }
}
```

Register in `META-INF/spring.factories` (or `META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor.imports` for Spring Boot 4.x):

```
org.springframework.boot.env.EnvironmentPostProcessor=\
  org.jds.edgar4j.config.ResourceModeProfileActivator
```

### Usage

```bash
# Low resource mode - no external dependencies
mvn spring-boot:run -Dedgar4j.resource-mode=low

# High resource mode (default) - requires MongoDB, PostgreSQL, Redis
mvn spring-boot:run -Dedgar4j.resource-mode=high

# Via environment variable
EDGAR4J_RESOURCE_MODE=low java -jar edgar4j.jar
```

## Profile-Specific Configuration Files

### application-resource-low.yml

```yaml
spring:
  autoconfigure:
    exclude:
      - org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration
      - org.springframework.boot.autoconfigure.data.mongo.MongoRepositoriesAutoConfiguration
      - org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration
      - org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration
      - org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration
      - org.springframework.boot.autoconfigure.data.elasticsearch.ElasticsearchDataAutoConfiguration
      - org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
      - org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration
      - org.springframework.boot.autoconfigure.data.jpa.JpaRepositoriesAutoConfiguration
  datasource:
    # H2 for Spring Batch metadata only
    url: jdbc:h2:file:${edgar4j.storage.file.base-path:./data}/batch/batch_metadata;AUTO_SERVER=TRUE
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    database-platform: org.hibernate.dialect.H2Dialect
    hibernate:
      ddl-auto: update
  batch:
    jdbc:
      initialize-schema: always
  cache:
    type: caffeine
    caffeine:
      spec: maximumSize=10000,expireAfterWrite=3600s

edgar4j:
  storage:
    file:
      base-path: ${EDGAR4J_DATA_PATH:./data}
      max-in-memory-records: ${EDGAR4J_MAX_MEMORY_RECORDS:100000}
      index-on-startup: true
      flush-interval: PT30S

logging:
  level:
    org.jds.edgar4j.adapter.file: DEBUG
    org.springframework.data: OFF
```

### application-resource-high.yml

```yaml
# This is essentially the current application.yml content.
# Move all MongoDB, PostgreSQL, Redis, Elasticsearch config here.
spring:
  data:
    mongodb:
      uri: ${MONGO_URL}
      database: ${MONGODB_DATABASE:edgar}
      auto-index-creation: true
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
    elasticsearch:
      repositories:
        enabled: ${ELASTICSEARCH_REPOSITORIES_ENABLED:false}
  datasource:
    url: jdbc:postgresql://localhost:5432/edgar4j
    username: edgar4j
    password: edgar4j
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: ${HIKARI_MAX_POOL_SIZE:15}
  jpa:
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
  cache:
    type: redis
```

## Auto-Configuration Classes

### LowResourceAutoConfiguration

```java
@Configuration
@Profile("resource-low")
@ConditionalOnProperty(name = "edgar4j.resource-mode", havingValue = "low")
public class LowResourceAutoConfiguration {

    @Bean
    public FileStorageEngine fileStorageEngine(FileStorageProperties properties) {
        return new FileStorageEngine(properties);
    }

    @Bean
    @Primary
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterWrite(Duration.ofHours(1)));
        return cacheManager;
    }

    @Bean
    public ResourceModeInfo resourceModeInfo() {
        return new ResourceModeInfo("low", "File-based storage (JSON/CSV/Parquet)");
    }
}
```

### HighResourceAutoConfiguration

```java
@Configuration
@Profile("resource-high")
@ConditionalOnProperty(name = "edgar4j.resource-mode", havingValue = "high", matchIfMissing = true)
public class HighResourceAutoConfiguration {

    @Bean
    public ResourceModeInfo resourceModeInfo() {
        return new ResourceModeInfo("high", "MongoDB + PostgreSQL + Redis");
    }
}
```

### ResourceModeInfo (Status Bean)

```java
/**
 * Exposes the current resource mode for health checks and REST API.
 */
public record ResourceModeInfo(String mode, String description) {}
```

Expose via Actuator:

```java
@Component
public class ResourceModeHealthIndicator implements HealthIndicator {
    private final ResourceModeInfo info;

    @Override
    public Health health() {
        return Health.up()
                .withDetail("resource-mode", info.mode())
                .withDetail("storage", info.description())
                .build();
    }
}
```

## Conditional Bean Registration

### MongoDB Repositories (Only in High Mode)

```java
@Configuration
@Profile("resource-high")
@EnableMongoRepositories(basePackages = "org.jds.edgar4j.adapter.mongo.internal")
public class MongoRepositoryConfig {
    // Existing MongoConfig content moves here
}
```

### JPA Repositories (Only in High Mode)

```java
@Configuration
@Profile("resource-high")
@EnableJpaRepositories(basePackages = "org.jds.edgar4j.adapter.jpa.internal")
public class JpaRepositoryConfig {
    // Existing JPA config moves here
}
```

### File Adapters (Only in Low Mode)

```java
@Configuration
@Profile("resource-low")
@ComponentScan(basePackages = "org.jds.edgar4j.adapter.file")
public class FileAdapterConfig {
    // Component scan picks up all @Component file adapters
}
```

## Startup Banner

Add a mode indicator to startup:

```java
@Component
@Slf4j
public class ResourceModeStartupLogger implements ApplicationListener<ApplicationReadyEvent> {

    private final ResourceModeInfo modeInfo;

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        log.info("╔══════════════════════════════════════╗");
        log.info("║  edgar4j Resource Mode: {}          ║", modeInfo.mode().toUpperCase());
        log.info("║  Storage: {}  ║", modeInfo.description());
        log.info("╚══════════════════════════════════════╝");
    }
}
```

## Refactored application.yml (Shared Config Only)

The main `application.yml` should only contain mode-independent configuration:

```yaml
server:
  port: 8080

spring:
  application:
    name: edgar4j
  cloud:
    config:
      enabled: ${SPRING_CLOUD_CONFIG_ENABLED:false}
  batch:
    job:
      enabled: false
  task:
    execution:
      pool:
        core-size: 8
        max-size: 20

edgar4j:
  resource-mode: ${EDGAR4J_RESOURCE_MODE:high}
  sec:
    user-agent: ${SEC_USER_AGENT:}
  urls:
    # ... all SEC URLs (mode-independent)
  jobs:
    # ... all job cron configs (mode-independent)
  providers:
    # ... all market data provider configs (mode-independent)
  export:
    max-records: ${EDGAR4J_EXPORT_MAX_RECORDS:10000}

management:
  # ... actuator config (mode-independent)

logging:
  # ... logging config (mode-independent)
```

## Excluded Auto-Configurations Summary

| Auto-Configuration | Excluded In |
|-------------------|------------|
| `MongoAutoConfiguration` | `resource-low` |
| `MongoDataAutoConfiguration` | `resource-low` |
| `MongoRepositoriesAutoConfiguration` | `resource-low` |
| `RedisAutoConfiguration` | `resource-low` |
| `ElasticsearchDataAutoConfiguration` | `resource-low` |
| `DataSourceAutoConfiguration` | Neither (H2 used in low, PostgreSQL in high) |
| `HibernateJpaAutoConfiguration` | `resource-low` (unless H2 Batch needs it) |

## Validation Checklist

- [ ] `mvn spring-boot:run -Dedgar4j.resource-mode=low` starts with zero external services
- [ ] `mvn spring-boot:run -Dedgar4j.resource-mode=high` starts identically to current behavior
- [ ] Health endpoint shows current resource mode
- [ ] No `NoSuchBeanDefinitionException` in either mode
- [ ] No MongoDB/PostgreSQL/Redis connection attempts in low mode
- [ ] Startup logs clearly indicate which mode is active
- [ ] Environment variable `EDGAR4J_RESOURCE_MODE` overrides YAML default

## Estimated Effort

- **Profile YAML files**: 0.5 day
- **Auto-configuration classes**: 1 day
- **EnvironmentPostProcessor**: 0.5 day
- **Refactor existing configs for profile awareness**: 1 day
- **Startup validation and health indicators**: 0.5 day
- **Total**: 3-4 days
