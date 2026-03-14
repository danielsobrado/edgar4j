package org.jds.edgar4j.batch.config;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.jds.edgar4j.properties.Edgar4JProperties;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;

class FileFlushChunkListenerTest {

    @Test
    void flushesAfterChunkWhenEnabled() {
        FileStorageEngine fileStorageEngine = mock(FileStorageEngine.class);
        Edgar4JProperties properties = new Edgar4JProperties();
        properties.getBatch().setFlushAfterChunk(true);
        FileFlushChunkListener listener = new FileFlushChunkListener(fileStorageEngine, properties);

        listener.afterChunk((ChunkContext) null);
        listener.afterChunkError(null);

        verify(fileStorageEngine, times(2)).flushAll();
    }

    @Test
    void skipsFlushWhenDisabled() {
        FileStorageEngine fileStorageEngine = mock(FileStorageEngine.class);
        Edgar4JProperties properties = new Edgar4JProperties();
        properties.getBatch().setFlushAfterChunk(false);
        FileFlushChunkListener listener = new FileFlushChunkListener(fileStorageEngine, properties);

        listener.afterChunk((ChunkContext) null);
        listener.afterChunkError(null);

        verify(fileStorageEngine, never()).flushAll();
    }
}