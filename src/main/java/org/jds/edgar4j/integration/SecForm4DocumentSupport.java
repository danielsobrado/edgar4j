package org.jds.edgar4j.integration;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class SecForm4DocumentSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SecForm4DocumentSupport() {
    }

    public static List<String> parseDailyMasterIndex(String indexContent) {
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

    public static String extractAccessionFromFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return null;
        }

        String[] parts = filename.split("/");
        return parts.length >= 4 ? parts[3] : null;
    }

    public static String extractCikFromAccessionNumber(String accessionNumber) {
        if (accessionNumber == null || !accessionNumber.matches("\\d{10}-\\d{2}-\\d{6}")) {
            return null;
        }
        return accessionNumber.substring(0, 10);
    }

    public static String selectPrimaryXmlDocument(String filingIndexJson) {
        try {
            JsonNode items = OBJECT_MAPPER.readTree(filingIndexJson).path("directory").path("item");
            if (!items.isArray()) {
                return null;
            }

            String firstXmlDocument = null;
            for (JsonNode item : items) {
                String name = item.path("name").asText(null);
                if (name == null || !name.toLowerCase().endsWith(".xml")) {
                    continue;
                }

                String lowerName = name.toLowerCase();
                if (lowerName.endsWith(".xsd") || lowerName.equals("filingsummary.xml")) {
                    continue;
                }

                if (lowerName.contains("form4") || lowerName.contains("doc4") || lowerName.contains("ownership")) {
                    return name;
                }

                if (firstXmlDocument == null) {
                    firstXmlDocument = name;
                }
            }

            return firstXmlDocument;
        } catch (Exception e) {
            return null;
        }
    }
}
