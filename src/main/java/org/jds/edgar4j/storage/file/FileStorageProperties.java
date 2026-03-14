package org.jds.edgar4j.storage.file;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "edgar4j.storage.file")
public class FileStorageProperties {

    private String basePath = "./data";
    private String collectionsPath = "collections";
    private boolean indexOnStartup = true;
    private boolean flushOnWrite = true;
    private Path resolvedBaseDirectory = Paths.get(basePath);
    private Path resolvedCollectionsDirectory = resolvedBaseDirectory.resolve(collectionsPath);

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
        refreshResolvedPaths();
    }

    public String getCollectionsPath() {
        return collectionsPath;
    }

    public void setCollectionsPath(String collectionsPath) {
        this.collectionsPath = collectionsPath;
        refreshResolvedPaths();
    }

    public boolean isIndexOnStartup() {
        return indexOnStartup;
    }

    public void setIndexOnStartup(boolean indexOnStartup) {
        this.indexOnStartup = indexOnStartup;
    }

    public boolean isFlushOnWrite() {
        return flushOnWrite;
    }

    public void setFlushOnWrite(boolean flushOnWrite) {
        this.flushOnWrite = flushOnWrite;
    }

    public Path resolveBaseDirectory() {
        return resolvedBaseDirectory;
    }

    public Path resolveCollectionsDirectory() {
        return resolvedCollectionsDirectory;
    }

    private void refreshResolvedPaths() {
        String effectiveBasePath = (basePath == null || basePath.isBlank()) ? "./data" : basePath;
        String effectiveCollectionsPath = (collectionsPath == null || collectionsPath.isBlank()) ? "collections" : collectionsPath;
        resolvedBaseDirectory = Paths.get(effectiveBasePath);
        resolvedCollectionsDirectory = resolvedBaseDirectory.resolve(effectiveCollectionsPath);
    }
}
