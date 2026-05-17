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

    @Test
    @DisplayName("extract should detect suspension language without a declaration")
    void extractShouldDetectSuspensionLanguage() {
        Filling filing = Filling.builder()
                .cik("0000123456")
                .accessionNumber("0000123456-20-000010")
                .formType(FormType.builder().number("8-K").build())
                .fillingDate(date(LocalDate.of(2020, 4, 1)))
                .primaryDocument("d8k.htm")
                .build();

        String rawDocument = """
                <html><body>
                <div>Item 8.01 Other Events</div>
                <p>Due to market uncertainty, the Company suspended its quarterly dividend effective immediately.</p>
                </body></html>
                """;

        var events = extractor.extract(rawDocument, filing, "https://www.sec.gov/Archives/suspension.htm");

        var suspension = events.stream()
                .filter(event -> event.eventType() == DividendEventsResponse.EventType.SUSPENSION)
                .findFirst()
                .orElseThrow();

        assertEquals(DividendEventsResponse.EventConfidence.HIGH, suspension.confidence());
        assertTrue(events.stream().noneMatch(event -> event.eventType() == DividendEventsResponse.EventType.DECLARATION));
    }

    @Test
    @DisplayName("extract should detect special dividends")
    void extractShouldDetectSpecialDividend() {
        Filling filing = Filling.builder()
                .cik("0000123456")
                .accessionNumber("0000123456-26-000403")
                .formType(FormType.builder().number("8-K").build())
                .fillingDate(date(LocalDate.of(2026, 3, 1)))
                .primaryDocument("d8k.htm")
                .build();

        String rawDocument = """
                <html><body>
                <div>Item 8.01 Other Events</div>
                <p>The Board of Directors declared a special cash dividend of $5.00 per share payable on
                April 15, 2026 to stockholders of record on March 31, 2026.</p>
                </body></html>
                """;

        var events = extractor.extract(rawDocument, filing, "https://www.sec.gov/Archives/special.htm");

        var special = events.stream()
                .filter(event -> event.eventType() == DividendEventsResponse.EventType.SPECIAL)
                .findFirst()
                .orElseThrow();

        assertEquals(DividendEventsResponse.DividendType.SPECIAL, special.dividendType());
        assertEquals(5.00d, special.amountPerShare().doubleValue(), 0.000001d);
    }

    @Test
    @DisplayName("extract should not false-positive on dividend yield discussion")
    void extractShouldNotFalsePositiveOnYieldDiscussion() {
        Filling filing = Filling.builder()
                .cik("0000123456")
                .accessionNumber("0000123456-26-000404")
                .formType(FormType.builder().number("10-K").build())
                .fillingDate(date(LocalDate.of(2026, 2, 28)))
                .primaryDocument("d10k.htm")
                .build();

        String rawDocument = """
                <html><body>
                <p>The current dividend yield is approximately 2.5%, which is competitive with peers.</p>
                <p>The company discusses dividend policy generally but no distribution was declared.</p>
                </body></html>
                """;

        var events = extractor.extract(rawDocument, filing, "https://www.sec.gov/Archives/yield.htm");

        assertTrue(events.isEmpty());
    }

    @Test
    @DisplayName("cleanDocumentText should remove inline XBRL tags without losing text")
    void cleanDocumentTextShouldHandleInlineXbrlMarkup() {
        String rawDocument = """
                <html><body>
                <ix:nonNumeric name="dei:DocumentType">8-K</ix:nonNumeric>
                <p>The Board of Directors declared a cash dividend of
                <ix:nonFraction name="us-gaap:CommonStockDividendsPerShareDeclared">$0.42</ix:nonFraction>
                per share.</p>
                </body></html>
                """;

        String cleaned = extractor.cleanDocumentText(rawDocument);

        assertTrue(cleaned.contains("8-K"));
        assertTrue(cleaned.contains("declared a cash dividend"));
        assertTrue(cleaned.contains("$0.42"));
        assertFalse(cleaned.contains("ix:nonFraction"));
    }

    private Date date(LocalDate value) {
        return Date.from(value.atStartOfDay().toInstant(ZoneOffset.UTC));
    }
}
