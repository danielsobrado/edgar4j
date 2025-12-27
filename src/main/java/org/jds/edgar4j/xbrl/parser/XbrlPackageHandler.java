package org.jds.edgar4j.xbrl.parser;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.jds.edgar4j.xbrl.model.XbrlInstance;
import org.springframework.stereotype.Component;

import java.io.*;
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
            } else if (isInstanceFile(filename)) {
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
    private boolean isInstanceFile(String filename) {
        String lower = filename.toLowerCase();

        // Skip linkbases
        if (lower.endsWith("_cal.xml") || lower.endsWith("_def.xml")
                || lower.endsWith("_lab.xml") || lower.endsWith("_pre.xml")) {
            return false;
        }

        // Check for common instance patterns
        if (lower.endsWith("_htm.xml")) return true;  // SEC inline XBRL
        if (lower.matches(".*-\\d{8}\\.xml")) return true;  // Traditional
        if (lower.endsWith(".xml") && !lower.endsWith(".xsd")) {
            // Could be instance, check content later
            return true;
        }

        return false;
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
            // Return the first instance (usually the main document)
            return instances.isEmpty() ? null : instances.values().iterator().next();
        }

        public int getTotalFacts() {
            return instances.values().stream()
                    .mapToInt(i -> i.getFacts().size())
                    .sum();
        }
    }
}
