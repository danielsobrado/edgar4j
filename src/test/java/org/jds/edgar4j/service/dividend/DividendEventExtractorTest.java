package org.jds.edgar4j.service.dividend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;

import org.jds.edgar4j.dto.response.DividendEventsResponse;
import org.jds.edgar4j.integration.Form8KParser;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.FormType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DividendEventExtractorTest {

    private final DividendEventExtractor extractor = new DividendEventExtractor(new Form8KParser());

    @Test
    @DisplayName("extract should detect declaration amount and dates from Item 8.01 text")
    void extractShouldDetectDeclarationAmountAndDates() {
        Filling filing = Filling.builder()
                .cik("0000123456")
                .accessionNumber("0000123456-26-000401")
                .formType(FormType.builder().number("8-K").build())
                .fillingDate(date(LocalDate.of(2026, 1, 31)))
                .primaryDocument("d8k.htm")
                .build();

        String rawDocument = """
                <html><body>
                <div>Item 8.01 Other Events</div>
                <p>On January 30, 2026, the Board of Directors declared a quarterly cash dividend of $0.42 per share.
                The dividend is payable on February 28, 2026 to stockholders of record on February 14, 2026.</p>
                </body></html>
                """;

        var events = extractor.extract(rawDocument, filing, "https://www.sec.gov/Archives/example.htm");

        assertFalse(events.isEmpty());
        var declaration = events.stream()
                .filter(event -> event.eventType() == DividendEventsResponse.EventType.DECLARATION)
                .findFirst()
                .orElseThrow();

        assertEquals(LocalDate.of(2026, 1, 31), declaration.filedDate());
        assertEquals(LocalDate.of(2026, 1, 30), declaration.declarationDate());
        assertEquals(LocalDate.of(2026, 2, 14), declaration.recordDate());
        assertEquals(LocalDate.of(2026, 2, 28), declaration.payableDate());
        assertNotNull(declaration.amountPerShare());
        assertEquals(0.42d, declaration.amountPerShare().doubleValue(), 0.000001d);
        assertEquals(DividendEventsResponse.DividendType.QUARTERLY, declaration.dividendType());
        assertTrue(declaration.textSnippet().contains("declared a quarterly cash dividend"));
    }

    @Test
    @DisplayName("extract should detect policy language from annual filing text")
    void extractShouldDetectPolicyLanguageFromAnnualFiling() {
        Filling filing = Filling.builder()
                .cik("0000123456")
                .accessionNumber("0000123456-26-000402")
                .formType(FormType.builder().number("10-K").build())
                .fillingDate(date(LocalDate.of(2026, 2, 28)))
                .primaryDocument("d10k.htm")
                .build();

        String rawDocument = """
                <html><body>
                <p>Future dividends remain at the discretion of the board and depend on earnings, capital requirements,
                and debt covenant restrictions.</p>
                </body></html>
                """;

        var events = extractor.extract(rawDocument, filing, "https://www.sec.gov/Archives/annual.htm");

        var policyEvent = events.stream()
                .filter(event -> event.eventType() == DividendEventsResponse.EventType.POLICY_CHANGE)
                .findFirst()
                .orElseThrow();

        assertTrue(policyEvent.policyLanguage().contains("discretion of the board"));
        assertEquals(DividendEventsResponse.EventConfidence.LOW, policyEvent.confidence());
    }

    private Date date(LocalDate value) {
        return Date.from(value.atStartOfDay().toInstant(ZoneOffset.UTC));
    }
}
