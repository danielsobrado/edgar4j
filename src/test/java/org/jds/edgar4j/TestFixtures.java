package org.jds.edgar4j;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import org.jds.edgar4j.model.Company;
import org.jds.edgar4j.model.Exchange;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form4Transaction;
import org.jds.edgar4j.model.FormType;
import org.jds.edgar4j.model.Ticker;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.jds.edgar4j.storage.file.FileStorageProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

public final class TestFixtures {

    private TestFixtures() {
    }

    public static Form4 createTestForm4(String accessionNumber, String symbol) {
        return createTestForm4(accessionNumber, symbol, LocalDate.of(2024, 1, 15));
    }

    public static Form4 createTestForm4(String accessionNumber, String symbol, LocalDate transactionDate) {
        return Form4.builder()
                .id("form4-" + accessionNumber)
                .accessionNumber(accessionNumber)
                .documentType("4")
                .cik("0000320193")
                .issuerName(symbol + " INC")
                .tradingSymbol(symbol)
                .rptOwnerCik("0001000001")
                .rptOwnerName("Jane Doe")
                .isDirector(false)
                .isOfficer(true)
                .isTenPercentOwner(false)
                .isOther(false)
                .ownerType("Officer")
                .officerTitle("CFO")
                .transactionDate(transactionDate)
                .transactionShares(100f)
                .transactionPricePerShare(50f)
                .transactionValue(5000f)
                .acquiredDisposedCode("A")
                .transactions(List.of(Form4Transaction.builder()
                        .accessionNumber(accessionNumber)
                        .transactionType("NON_DERIVATIVE")
                        .transactionCode("P")
                        .transactionDate(transactionDate)
                        .transactionShares(100f)
                        .transactionPricePerShare(50f)
                        .transactionValue(5000f)
                        .acquiredDisposedCode("A")
                        .build()))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    public static Form4 createBuyForm4(String accessionNumber, String symbol, float value) {
        Form4 form4 = createTestForm4(accessionNumber, symbol);
        form4.setTransactionValue(value);
        form4.setAcquiredDisposedCode("A");
        return form4;
    }

    public static Form4 createSellForm4(String accessionNumber, String symbol, float value) {
        Form4 form4 = createTestForm4(accessionNumber, symbol);
        form4.setTransactionValue(value);
        form4.setAcquiredDisposedCode("D");
        return form4;
    }

    public static Company createTestCompany(String cik, String ticker) {
        return Company.builder()
                .id("company-" + cik)
                .cik(cik)
                .ticker(ticker)
                .name(ticker + " Corp")
                .description("Test company for " + ticker)
                .taxonomy("operating")
                .build();
    }

    public static Ticker createTestTicker(String code, String cik) {
        return Ticker.builder()
                .id("ticker-" + code)
                .code(code)
                .cik(cik)
                .name(code + " Holdings")
                .exchange(Exchange.builder()
                        .id("exchange-nasdaq")
                        .code("NASDAQ")
                        .name("Nasdaq")
                        .country("US")
                        .build())
                .build();
    }

    public static Filling createTestFilling(String accessionNumber, String cik, String formTypeNumber, LocalDate filingDate) {
        return Filling.builder()
                .id("filling-" + accessionNumber)
                .accessionNumber(accessionNumber)
                .company("Test Filing Co")
                .cik(cik)
                .formType(createTestFormType(formTypeNumber))
                .fillingDate(asDate(filingDate))
                .reportDate(asDate(filingDate.minusDays(10)))
                .url("https://www.sec.gov/Archives/" + accessionNumber)
                .primaryDocument("primary.htm")
                .primaryDocDescription("Primary Document")
                .isXBRL(true)
                .isInlineXBRL("10-K".equalsIgnoreCase(formTypeNumber))
                .build();
    }

    public static FormType createTestFormType(String number) {
        return FormType.builder()
                .id("form-type-" + number)
                .number(number)
                .description("Form " + number)
                .build();
    }

    public static FileStorageEngine newFileStorageEngine(java.nio.file.Path tempDir) {
        FileStorageProperties properties = new FileStorageProperties();
        properties.setBasePath(tempDir.toAbsolutePath().toString());
        properties.setCollectionsPath("collections");
        properties.setIndexOnStartup(true);
        properties.setFlushOnWrite(true);

        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        return new FileStorageEngine(properties, objectMapper);
    }

    private static Date asDate(LocalDate value) {
        return Date.from(value.atStartOfDay().toInstant(ZoneOffset.UTC));
    }
}
