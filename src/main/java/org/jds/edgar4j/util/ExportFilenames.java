package org.jds.edgar4j.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Builds export attachment filenames with an appended local date-and-time stamp so downloads are
 * uniquely named and self-document when they were produced
 * (e.g. {@code filings-export-20260530-143015.csv}).
 */
public final class ExportFilenames {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private ExportFilenames() {
    }

    public static String timestamped(String base, String extension) {
        return base + "-" + LocalDateTime.now().format(TIMESTAMP) + "." + extension;
    }
}
