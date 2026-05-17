package org.jds.edgar4j.service.xbrl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.jds.edgar4j.service.xbrl.SecBulkNotesDatasetParser.DimRecord;
import org.jds.edgar4j.service.xbrl.SecBulkNotesDatasetParser.DividendClassification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SecBulkNotesDatasetParserTest {

    private final SecBulkNotesDatasetParser parser = new SecBulkNotesDatasetParser();

    @Test
    @DisplayName("parseNumLines should parse tab-separated NUM records with blank optional fields")
    void parseNumLinesShouldParseTabSeparatedNumRecordsWithBlankOptionalFields() {
        List<SecBulkNotesDatasetParser.NumRecord> records = parser.parseNumLines(List.of(
                "adsh\ttag\tversion\tcoreg\tddate\tqtrs\tuom\tvalue\tdimh\tiprx",
                "0000320193-25-000081\tCommonStockDividendsPerShareDeclared\tus-gaap/2025\t\t20250927\t4\tUSD/shares\t1.04\t0x00000000\t1"));

        assertEquals(1, records.size());
        SecBulkNotesDatasetParser.NumRecord record = records.get(0);
        assertEquals("0000320193-25-000081", record.accession());
        assertEquals(LocalDate.of(2025, 9, 27), record.periodEnd());
        assertEquals(new BigDecimal("1.04"), record.value());
        assertNull(record.coRegistrant());
        assertEquals(1, record.inlineXbrlPriority());
    }

    @Test
    @DisplayName("findCustomDividendTags should detect dividend-like custom tags")
    void findCustomDividendTagsShouldDetectDividendLikeCustomTags() {
        List<SecBulkNotesDatasetParser.CustomDividendTag> tags = parser.findCustomDividendTags(List.of(
                "tag\tversion\tcustom\tlabel",
                "BoardDeclaredDividendPerShare\t0000320193-25-000081\t1\tBoard declared dividend per share",
                "RevenueMetric\t0000320193-25-000081\t1\tRevenue metric",
                "CommonStockDividendsPerShareDeclared\tus-gaap/2025\t0\tCommon dividends"));

        assertEquals(1, tags.size());
        assertEquals("BoardDeclaredDividendPerShare", tags.get(0).tag());
    }

    @Test
    @DisplayName("classifyDividendDimension should identify consolidated common and preferred dimensions")
    void classifyDividendDimensionShouldIdentifyDividendDimensions() {
        Map<String, DimRecord> dimensions = Map.of(
                "0xcommon", new DimRecord("0xcommon", "StatementClassOfStockAxis", "CommonStockMember"),
                "0xpreferred", new DimRecord("0xpreferred", "StatementClassOfStockAxis", "SeriesAPreferredStockMember"));

        assertEquals(DividendClassification.CONSOLIDATED,
                parser.classifyDividendDimension("0x00000000", dimensions));
        assertEquals(DividendClassification.COMMON,
                parser.classifyDividendDimension("0xcommon", dimensions));
        assertEquals(DividendClassification.PREFERRED,
                parser.classifyDividendDimension("0xpreferred", dimensions));
        assertEquals(DividendClassification.UNKNOWN,
                parser.classifyDividendDimension("0xmissing", dimensions));
    }

    @Test
    @DisplayName("deduplicateByInlinePriority should select iprx priority 1 facts")
    void deduplicateByInlinePriorityShouldSelectPriorityOneFacts() {
        List<SecBulkNotesDatasetParser.NumRecord> deduplicated = parser.deduplicateByInlinePriority(parser.parseNumLines(List.of(
                "adsh\ttag\tversion\tcoreg\tddate\tqtrs\tuom\tvalue\tdimh\tiprx",
                "0000320193-25-000081\tCommonStockDividendsPerShareDeclared\tus-gaap/2025\t\t20250927\t4\tUSD/shares\t0.99\t0x00000000\t2",
                "0000320193-25-000081\tCommonStockDividendsPerShareDeclared\tus-gaap/2025\t\t20250927\t4\tUSD/shares\t1.04\t0x00000000\t1")));

        assertEquals(1, deduplicated.size());
        assertEquals(new BigDecimal("1.04"), deduplicated.get(0).value());
        assertEquals(1, deduplicated.get(0).inlineXbrlPriority());
    }
}
