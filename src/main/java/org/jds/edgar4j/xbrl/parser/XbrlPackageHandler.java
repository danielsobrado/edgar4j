package org.jds.edgar4j.xbrl.parser;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.jds.edgar4j.xbrl.model.XbrlInstance;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Handles XBRL filing packages (ZIP files) from SEC.
 * Supports extraction and parsing of multi-document filings.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class XbrlPackageHandler {

    private final XbrlParser xbrlParser;

    // Common file patterns
    private static final List<String> INSTANCE_PATTERNS = Arrays.asList(
            ".*_htm\\.xml$",           // SEC inline XBRL
            ".*-\\d{8}\\.xml$",        // Traditional format
            ".*_cal\\.xml$",           // Calculation linkbase (skip)
            ".*_def\\.xml$",           // Definition linkbase (skip)
            ".*_lab\\.xml$",           // Label linkbase (skip)
            ".*_pre\\.xml$"            // Presentation linkbase (skip)
    );

    private static final List<String> INLINE_PATTERNS = Arrays.asList(
            ".*\\.htm$",
            ".*\\.html$",
            ".*_htm\\.xml$"
    );

    /**
     * Parse an XBRL package (ZIP file) and return all instances.
     */
    public PackageResult parsePackage(byte[] zipContent, String packageUri) {
        PackageResult result = new PackageResult();
        result.setPackageUri(packageUri);

        Map<String, byte[]> files = extractZip(zipContent);
        result.setTotalFiles(files.size());

        // Categorize files
        List<String> instanceFiles = new ArrayList<>();
        List<String> linkbaseFiles = new ArrayList<>();
        List<String> schemaFiles = new ArrayList<>();
        List<String> otherFiles = new ArrayList<>();

        for (String filename : files.keySet()) {
            String lower = filename.toLowerCase();
            if (lower.endsWith("_cal.xml") || lower.endsWith("_def.xml")
                    || lower.endsWith("_lab.xml") || lower.endsWith("_pre.xml")) {
                linkbaseFiles.add(filename);
            } else if (lower.endsWith(".xsd")) {
                schemaFiles.add(filename);
            } else if (isInstanceFile(filename, files.get(filename))) {
                instanceFiles.add(filename);
            } else {
                otherFiles.add(filename);
            }
        }

        result.setInstanceFiles(instanceFiles);
        result.setLinkbaseFiles(linkbaseFiles);
        result.setSchemaFiles(schemaFiles);

        // Parse instance documents
        for (String instanceFile : instanceFiles) {
            try {
                byte[] content = files.get(instanceFile);
                String contentType = detectContentType(instanceFile);

                XbrlInstance instance = xbrlParser.parse(content, instanceFile, contentType);
                result.getInstances().put(instanceFile, instance);

            } catch (Exception e) {
                log.warn("Failed to parse instance file {}: {}", instanceFile, e.getMessage());
                result.getErrors().put(instanceFile, e.getMessage());
            }
        }

        // If no instances found, try parsing HTML files
        if (result.getInstances().isEmpty()) {
            for (String filename : files.keySet()) {
                if (isInlineFile(filename)) {
                    try {
                        byte[] content = files.get(filename);
                        XbrlInstance instance = xbrlParser.parse(content, filename, "text/html");

                        if (instance != null && !instance.getFacts().isEmpty()) {
                            result.getInstances().put(filename, instance);
                            break;  // Usually only need primary document
                        }
                    } catch (Exception e) {
                        log.trace("File {} is not iXBRL: {}", filename, e.getMessage());
                    }
                }
            }
        }

        return result;
    }

    /**
     * Parse a single XBRL or iXBRL file.
     */
    public XbrlInstance parseFile(byte[] content, String filename) {
        String contentType = detectContentType(filename);
        return xbrlParser.parse(content, filename, contentType);
    }

    /**
     * Extract files from a ZIP archive.
     */
    private Map<String, byte[]> extractZip(byte[] zipContent) {
        Map<String, byte[]> files = new LinkedHashMap<>();

        try (ZipArchiveInputStream zis = new ZipArchiveInputStream(
                new ByteArrayInputStream(zipContent))) {

            ZipArchiveEntry entry;
            while ((entry = zis.getNextZipEntry()) != null) {
                if (entry.isDirectory()) continue;

                String name = entry.getName();
                // Skip hidden files and directories
                if (name.startsWith(".") || name.contains("/.")) continue;

                // Read file content
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    baos.write(buffer, 0, len);
                }

                files.put(name, baos.toByteArray());
            }

        } catch (IOException e) {
            log.error("Failed to extract ZIP: {}", e.getMessage());
        }

        return files;
    }

    /**
     * Check if a file is an XBRL instance document.
     */
    private boolean isInstanceFile(String filename, byte[] content) {
        String lower = filename.toLowerCase();

        // Skip linkbases
        if (lower.endsWith("_cal.xml") || lower.endsWith("_def.xml")
                || lower.endsWith("_lab.xml") || lower.endsWith("_pre.xml")) {
            return false;
        }

        if (isNonInstanceName(lower)) {
            return false;
        }

        // Check for common instance patterns
        if (lower.endsWith("_htm.xml")) return true;  // SEC inline XBRL
        if (lower.matches(".*-\\d{8}\\.xml")) return true;  // Traditional
        if (lower.endsWith("_ins.xml") || lower.endsWith("_xbrl.xml")) return true;
        if (lower.endsWith(".xml") && !lower.endsWith(".xsd")) {
            return contentLooksLikeInstance(content);
        }

        return false;
    }

    private boolean isNonInstanceName(String lower) {
        if (lower.endsWith("filingsummary.xml")) {
            return true;
        }
        int lastSlash = Math.max(lower.lastIndexOf('/'), lower.lastIndexOf('\\'));
        String baseName = lastSlash >= 0 ? lower.substring(lastSlash + 1) : lower;
        return baseName.matches("r\\d+\\.xml");
    }

    private boolean contentLooksLikeInstance(byte[] content) {
        if (content == null || content.length == 0) {
            return false;
        }
        int length = Math.min(content.length, 4096);
        String header = new String(content, 0, length, StandardCharsets.US_ASCII).toLowerCase();

        if (header.contains("<xbrl") || header.contains("xbrli:xbrl")
                || header.contains("xmlns:xbrli") || header.contains("xbrl.org/2003/instance")) {
            return true;
        }

        return header.contains("xmlns:ix") || header.contains("ix:nonfraction")
                || header.contains("ix:nonnumeric") || header.contains("inlinexbrl");
    }

    /**
     * Check if a file could be an inline XBRL file.
     */
    private boolean isInlineFile(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".htm") || lower.endsWith(".html")
                || lower.endsWith("_htm.xml");
    }

    /**
     * Detect content type from filename.
     */
    private String detectContentType(String filename) {
        String lower = filename.toLowerCase();

        if (lower.endsWith(".htm") || lower.endsWith(".html")) {
            return "text/html";
        }
        if (lower.endsWith(".xml")) {
            return "application/xml";
        }
        if (lower.endsWith(".xsd")) {
            return "application/xml";
        }

        return "application/octet-stream";
    }

    /**
     * Result of parsing an XBRL package.
     */
    @Data
    public static class PackageResult {
        private String packageUri;
        private int totalFiles;
        private List<String> instanceFiles = new ArrayList<>();
        private List<String> linkbaseFiles = new ArrayList<>();
        private List<String> schemaFiles = new ArrayList<>();
        private Map<String, XbrlInstance> instances = new LinkedHashMap<>();
        private Map<String, String> errors = new HashMap<>();

        public boolean hasInstances() {
            return !instances.isEmpty();
        }

        public XbrlInstance getPrimaryInstance() {
            if (instances.isEmpty()) {
                return null;
            }

            return instances.values().stream()
                    .filter(Objects::nonNull)
                    .max(Comparator.comparingInt((XbrlInstance instance) -> instance.getFacts().size())
                            .thenComparingInt(instance -> instance.getContexts().size())
                            .thenComparingInt(instance -> instance.getUnits().size()))
                    .orElse(instances.values().iterator().next());
        }

        public int getTotalFacts() {
            return instances.values().stream()
                    .mapToInt(i -> i.getFacts().size())
                    .sum();
        }
    }
}
