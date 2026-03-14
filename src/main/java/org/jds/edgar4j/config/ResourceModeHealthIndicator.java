package org.jds.edgar4j.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.bson.Document;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import com.mongodb.client.MongoClient;

@Component
public class ResourceModeHealthIndicator implements HealthIndicator {

    private final ResourceModeInfo resourceModeInfo;
    private final ObjectProvider<FileStorageEngine> fileStorageEngineProvider;
    private final ObjectProvider<DataSource> dataSourceProvider;
    private final ObjectProvider<MongoClient> mongoClientProvider;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;

    public ResourceModeHealthIndicator(
            ResourceModeInfo resourceModeInfo,
            ObjectProvider<FileStorageEngine> fileStorageEngineProvider,
            ObjectProvider<DataSource> dataSourceProvider,
            ObjectProvider<MongoClient> mongoClientProvider,
            ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider) {
        this.resourceModeInfo = resourceModeInfo;
        this.fileStorageEngineProvider = fileStorageEngineProvider;
        this.dataSourceProvider = dataSourceProvider;
        this.mongoClientProvider = mongoClientProvider;
        this.redisConnectionFactoryProvider = redisConnectionFactoryProvider;
    }

    @Override
    public Health health() {
        if ("low".equalsIgnoreCase(resourceModeInfo.mode())) {
            FileStorageEngine fileStorageEngine = fileStorageEngineProvider.getIfAvailable();
            DependencyCheck storageCheck = validateFileStorage(fileStorageEngine);
            DependencyCheck batchCheck = validateDataSource(dataSourceProvider.getIfAvailable());
            Health.Builder builder = builder(storageCheck.healthy() && batchCheck.healthy())
                    .withDetail("storage", "file")
                    .withDetail("fileStorage", storageCheck.detail())
                    .withDetail("batchMetadataDatabase", batchCheck.detail());

            if (fileStorageEngine != null) {
                builder.withDetail("dataPath", fileStorageEngine.getProperties().getBasePath())
                        .withDetail("collectionsLoaded", fileStorageEngine.getRegisteredCollectionCount())
                        .withDetail("collectionNames", fileStorageEngine.getRegisteredCollectionNames())
                        .withDetail("totalRecordsInMemory", fileStorageEngine.getTotalRecordsInMemory());
            }
            return builder.build();
        }

        DependencyCheck mongoCheck = validateMongo();
        DependencyCheck postgresCheck = validateDataSource(dataSourceProvider.getIfAvailable());
        DependencyCheck redisCheck = validateRedis();

        Health.Builder builder = builder(mongoCheck.healthy() && postgresCheck.healthy() && redisCheck.healthy())
                .withDetail("storage", "mongo-postgres-redis")
                .withDetail("mongodb", mongoCheck.detail())
                .withDetail("postgresql", postgresCheck.detail())
                .withDetail("redis", redisCheck.detail());
        return builder.build();
    }

    private Health.Builder builder(boolean healthy) {
        return (healthy ? Health.up() : Health.down())
                .withDetail("resourceMode", resourceModeInfo.mode())
                .withDetail("description", resourceModeInfo.description());
    }

    private DependencyCheck validateFileStorage(FileStorageEngine fileStorageEngine) {
        if (fileStorageEngine == null) {
            return new DependencyCheck(false, "unavailable");
        }

        Path collectionsDirectory = fileStorageEngine.getProperties().resolveCollectionsDirectory();
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("directory", collectionsDirectory.toAbsolutePath().toString());

        try {
            Files.createDirectories(collectionsDirectory);
            Path probe = Files.createTempFile(collectionsDirectory, "health-check-", ".tmp");
            Files.deleteIfExists(probe);
            detail.put("writable", true);
            return new DependencyCheck(true, detail);
        } catch (IOException e) {
            detail.put("writable", false);
            detail.put("error", e.getClass().getSimpleName());
            return new DependencyCheck(false, detail);
        }
    }

    private DependencyCheck validateDataSource(DataSource dataSource) {
        if (dataSource == null) {
            return new DependencyCheck(false, "unavailable");
        }

        try (java.sql.Connection connection = dataSource.getConnection()) {
            if (!connection.isValid(2)) {
                return new DependencyCheck(false, "invalid");
            }
            return new DependencyCheck(true, connection.getMetaData().getURL());
        } catch (Exception e) {
            return new DependencyCheck(false, "unavailable: " + e.getClass().getSimpleName());
        }
    }

    private DependencyCheck validateMongo() {
        MongoClient mongoClient = mongoClientProvider.getIfAvailable();
        if (mongoClient == null) {
            return new DependencyCheck(false, "unavailable");
        }

        try {
            mongoClient.getDatabase("admin").runCommand(new Document("ping", 1));
            return new DependencyCheck(true, "reachable");
        } catch (Exception e) {
            return new DependencyCheck(false, "unavailable: " + e.getClass().getSimpleName());
        }
    }

    private DependencyCheck validateRedis() {
        RedisConnectionFactory redisConnectionFactory = redisConnectionFactoryProvider.getIfAvailable();
        if (redisConnectionFactory == null) {
            return new DependencyCheck(false, "unavailable");
        }

        try (RedisConnection connection = redisConnectionFactory.getConnection()) {
            String response = connection.ping();
            return new DependencyCheck("PONG".equalsIgnoreCase(response), response);
        } catch (Exception e) {
            return new DependencyCheck(false, "unavailable: " + e.getClass().getSimpleName());
        }
    }

    private record DependencyCheck(boolean healthy, Object detail) {
    }
}
