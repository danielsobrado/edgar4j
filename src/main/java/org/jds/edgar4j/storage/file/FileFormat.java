package org.jds.edgar4j.storage.file;

public enum FileFormat {
    JSON(".json"),
    JSONL(".jsonl");

    private final String extension;

    FileFormat(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }
}
