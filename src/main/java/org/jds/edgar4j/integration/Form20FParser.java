package org.jds.edgar4j.integration;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.jds.edgar4j.model.Form20F;
import org.jds.edgar4j.xbrl.XbrlService;
import org.jds.edgar4j.xbrl.model.XbrlInstance;
import org.jds.edgar4j.xbrl.sec.SecFilingExtractor.SecFilingMetadata;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Parses Form 20-F filings using the XBRL parser and SEC metadata extractor.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class Form20FParser {

    private final XbrlService xbrlService;

    public Form20F parse(String rawDocument, String accessionNumber, String documentName) {
        if (rawDocument == null || rawDocument.isBlank()) {
            return null;
        }

        try {
            byte[] content = rawDocument.getBytes(StandardCharsets.UTF_8);
            XbrlInstance instance = xbrlService.parse(content, documentName);
            if (instance == null) {
                return null;
            }

            SecFilingMetadata metadata = xbrlService.extractSecMetadata(instance);
            Map<String, BigDecimal> keyFinancials = xbrlService.getKeyFinancials(instance);

            Form20F.Form20FBuilder builder = Form20F.builder()
                    .accessionNumber(accessionNumber);

            if (metadata != null) {
                builder
                        .companyName(metadata.getEntityName())
                        .cik(metadata.getCik())
                        .tradingSymbol(metadata.getTradingSymbol())
                        .securityExchange(metadata.getSecurityExchange())
                        .formType(metadata.getFormType())
                        .documentPeriodEndDate(metadata.getDocumentPeriodEndDate())
                        .fiscalYear(metadata.getFiscalYear())
                        .fiscalPeriod(metadata.getFiscalPeriod())
                        .fiscalYearEndDate(metadata.getFiscalYearEndDate())
                        .sharesOutstanding(metadata.getSharesOutstanding())
                        .isAmendment(metadata.isAmendment())
                        .deiData(metadata.getDeiData());
            }

            if (keyFinancials != null && !keyFinancials.isEmpty()) {
                builder.keyFinancials(keyFinancials);
            }

            Form20F form20F = builder.build();
            if (form20F.getFormType() == null || form20F.getFormType().isBlank()) {
                form20F.setFormType("20-F");
            }

            return form20F;
        } catch (Exception e) {
            log.error("Failed to parse Form 20-F for accession: {}", accessionNumber, e);
            return null;
        }
    }
}

