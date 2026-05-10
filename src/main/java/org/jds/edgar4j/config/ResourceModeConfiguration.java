package org.jds.edgar4j.config;

import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.jds.edgar4j.storage.file.FileStorageProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(FileStorageProperties.class)
public class ResourceModeConfiguration {

    @Bean
    @Profile("resource-low")
    public FileStorageEngine fileStorageEngine(FileStorageProperties properties, ObjectMapper objectMapper) {
        return new FileStorageEngine(properties, objectMapper);
    }

    @Bean
    @Profile("resource-low")
    public ResourceModeInfo lowResourceModeInfo() {
        return new ResourceModeInfo("low", "File-backed document storage with embedded H2");
    }

    @Bean
    @Profile("resource-high")
    @ConditionalOnMissingBean
    public ResourceModeInfo highResourceModeInfo() {
        return new ResourceModeInfo("high", "MongoDB-backed document storage");
    }

    @Bean
    @ConditionalOnMissingBean
    public ResourceModeInfo defaultResourceModeInfo(@Value("${edgar4j.resource-mode:high}") String mode) {
        if ("low".equalsIgnoreCase(mode)) {
            return new ResourceModeInfo("low", "File-backed document storage with embedded H2");
        }
        return new ResourceModeInfo("high", "MongoDB-backed document storage");
    }
}

