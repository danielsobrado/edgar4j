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

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public String getCollectionsPath() {
        return collectionsPath;
    }

    public void setCollectionsPath(String collectionsPath) {
        this.collectionsPath = collectionsPath;
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
        return Paths.get(basePath);
    }

    public Path resolveCollectionsDirectory() {
        return Paths.get(basePath).resolve(collectionsPath);
    }
}
