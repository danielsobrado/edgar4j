package org.jds.edgar4j.batch.config;

import org.jds.edgar4j.properties.Edgar4JProperties;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.batch.core.listener.ChunkListener;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@Profile("resource-low")
@RequiredArgsConstructor
public class FileFlushChunkListener implements ChunkListener<Object, Object> {

    private final FileStorageEngine fileStorageEngine;
    private final Edgar4JProperties properties;

    @Override
    public void afterChunk(ChunkContext context) {
        flushIfEnabled();
    }

    @Override
    public void afterChunkError(ChunkContext context) {
        flushIfEnabled();
    }

    private void flushIfEnabled() {
        if (properties.getBatch().isFlushAfterChunk()) {
            fileStorageEngine.flushAll();
        }
    }
}