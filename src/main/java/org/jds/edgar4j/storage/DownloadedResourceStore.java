package org.jds.edgar4j.storage;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

import org.jds.edgar4j.properties.StorageProperties;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class DownloadedResourceStore {

    private final StorageProperties storageProperties;

    public Optional<String> readText(String namespace, String source, Charset charset) {
        return readBytes(namespace, source)
                .map(bytes -> new String(bytes, charset));
    }

    public Optional<byte[]> readBytes(String namespace, String source) {
        Path path = resolvePath(namespace, source);
        if (!Files.exists(path)) {
            return Optional.empty();
        }

        try {
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length == 0) {
                return Optional.empty();
            }
            return Optional.of(bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read cached resource from " + path, e);
        }
    }

    public Path writeText(String namespace, String source, String content, Charset charset) {
        return writeBytes(namespace, source, content.getBytes(charset));
    }

    public Path writeBytes(String namespace, String source, byte[] content) {
        Path path = resolvePath(namespace, source);
        try {
            Files.createDirectories(path.getParent());
            Files.write(path, content);
            return path;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write cached resource to " + path, e);
        }
    }

    public Path resolvePath(String namespace, String source) {
        URI uri = URI.create(source);
        String host = sanitizeSegment(Optional.ofNullable(uri.getHost()).orElse("local"));
        String fileName = buildFileName(uri);
        return Path.of(storageProperties.getDownloadCachePath())
                .resolve(sanitizeSegment(namespace))
                .resolve(host)
                .resolve(fileName);
    }

    private String buildFileName(URI uri) {
        String path = Optional.ofNullable(uri.getPath()).orElse("");
        String lastSegment = path;
        int slashIndex = path.lastIndexOf('/');
        if (slashIndex >= 0 && slashIndex < path.length() - 1) {
            lastSegment = path.substring(slashIndex + 1);
        }

        if (lastSegment.isBlank()) {
            lastSegment = "resource";
        }

        int dotIndex = lastSegment.lastIndexOf('.');
        String stem = dotIndex > 0 ? lastSegment.substring(0, dotIndex) : lastSegment;
        String extension = dotIndex > 0 ? lastSegment.substring(dotIndex) : ".bin";
        String hash = sha256(uri.toString()).substring(0, 16);

        return sanitizeSegment(stem) + "__" + hash + sanitizeExtension(extension);
    }

    private String sanitizeSegment(String value) {
        String sanitized = value == null ? "default" : value.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "_")
                .replaceAll("^_+|_+$", "");
        return sanitized.isBlank() ? "default" : sanitized;
    }

    private String sanitizeExtension(String extension) {
        String sanitized = extension == null ? ".bin" : extension.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9.]+", "");
        return sanitized.startsWith(".") ? sanitized : "." + sanitized;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes()));
        } catch (NoSuchAlgorithmException e) {
            log.error("Missing SHA-256 algorithm", e);
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
