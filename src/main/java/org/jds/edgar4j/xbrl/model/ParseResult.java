package org.jds.edgar4j.xbrl.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Contains parsing results, warnings, and errors for an XBRL instance.
 */
@Data
public class ParseResult {

    private boolean success = true;
    private long parseTimeMs;

    private int totalFactsFound;
    private int successfullyParsedFacts;
    private int skippedFacts;

    private int totalContextsFound;
    private int totalUnitsFound;

    private int nestedFactsExtracted;     // iXBRL nested fact count
    private int continuationFactsResolved; // iXBRL continuation elements resolved

    private List<ParseWarning> warnings = new ArrayList<>();
    private List<ParseError> errors = new ArrayList<>();

    // Recovery statistics
    private int namespaceRecoveries;      // Times namespace fallback was used
    private int encodingRecoveries;       // Times encoding detection was needed
    private int malformedXmlRecoveries;   // Times error recovery parsing was used

    public void addWarning(String code, String message, int lineNumber) {
        warnings.add(new ParseWarning(code, message, lineNumber));
    }

    public void addError(String code, String message, int lineNumber) {
        success = false;
        errors.add(new ParseError(code, message, lineNumber));
    }

    public void addError(String code, String message, Exception cause) {
        success = false;
        errors.add(new ParseError(code, message, cause));
    }

    public double getSuccessRate() {
        if (totalFactsFound == 0) return 1.0;
        return (double) successfullyParsedFacts / totalFactsFound;
    }

    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Parsed %d/%d facts (%.1f%% success) in %dms",
                successfullyParsedFacts, totalFactsFound, getSuccessRate() * 100, parseTimeMs));

        if (nestedFactsExtracted > 0) {
            sb.append(String.format(", %d nested facts extracted", nestedFactsExtracted));
        }

        if (!warnings.isEmpty()) {
            sb.append(String.format(", %d warnings", warnings.size()));
        }

        if (!errors.isEmpty()) {
            sb.append(String.format(", %d errors", errors.size()));
        }

        return sb.toString();
    }

    @Data
    public static class ParseWarning {
        private final String code;
        private final String message;
        private final int lineNumber;
    }

    @Data
    public static class ParseError {
        private final String code;
        private final String message;
        private int lineNumber;
        private Exception cause;

        public ParseError(String code, String message, int lineNumber) {
            this.code = code;
            this.message = message;
            this.lineNumber = lineNumber;
        }

        public ParseError(String code, String message, Exception cause) {
            this.code = code;
            this.message = message;
            this.cause = cause;
        }
    }
}
