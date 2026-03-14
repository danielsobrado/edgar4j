package org.jds.edgar4j.service.insider.impl;

import java.util.ArrayList;
import java.util.List;

final class EdgarForm4ParsingUtils {

    private EdgarForm4ParsingUtils() {
    }

    static List<String> parseDailyMasterIndex(String indexContent) {
        List<String> accessionNumbers = new ArrayList<>();
        if (indexContent == null || indexContent.isBlank()) {
            return accessionNumbers;
        }

        String[] lines = indexContent.split("\\R");
        boolean dataSectionStarted = false;
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.startsWith("CIK|Company Name|Form Type|Date Filed|Filename")) {
                dataSectionStarted = true;
                continue;
            }
            if (!dataSectionStarted || line.isEmpty()) {
                continue;
            }

            String[] fields = line.split("\\|");
            if (fields.length < 5 || !"4".equals(fields[2].trim())) {
                continue;
            }

            String accessionNumber = extractAccessionFromFilename(fields[4].trim());
            if (accessionNumber != null) {
                accessionNumbers.add(accessionNumber);
            }
        }

        return accessionNumbers;
    }

    static String extractAccessionFromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }

        String[] parts = filename.split("/");
        return parts.length >= 4 ? parts[3] : null;
    }

    static String extractCikFromAccessionNumber(String accessionNumber) {
        if (accessionNumber == null || !accessionNumber.matches("\\d{10}-\\d{2}-\\d{6}")) {
            return null;
        }
        return accessionNumber.substring(0, 10);
    }
}