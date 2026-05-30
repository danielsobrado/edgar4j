package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jds.edgar4j.dto.request.InsiderActivityScreenRequest;
import org.jds.edgar4j.dto.response.InsiderActivityCoverageResponse;
import org.jds.edgar4j.dto.response.InsiderActivityResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form4Transaction;
import org.jds.edgar4j.port.Form4DataPort;
import org.jds.edgar4j.service.CompanyMarketDataService;
import org.jds.edgar4j.service.Sp500Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class InsiderActivityServiceImplTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 1, 20);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            BASE_DATE.atStartOfDay(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault());

    @Mock
    private Form4DataPort form4Repository;

    @Mock
    private CompanyMarketDataService companyMarketDataService;

    @Mock
    private Sp500Service sp500Service;

    private InsiderActivityServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InsiderActivityServiceImpl(
                form4Repository,
                companyMarketDataService,
                sp500Service,
                new ObjectMapper().findAndRegisterModules(),
                FIXED_CLOCK);
        // Use a lenient mock for shared fixtures so coverage-focused tests can avoid
        // unnecessary stubbing failures when they don't exercise market cap lookup logic.
        org.mockito.Mockito.lenient().when(sp500Service.getAllTickers()).thenReturn(Set.of("AAPL"));
    }

    @Test
    @DisplayName("latest sales preset returns open-market sales in transaction view")
    void latestSalesPresetReturnsSales() {
        Form4 form = createForm4("0001", "AAPL", "Alice Seller", "0000000001");
        form.setTransactions(List.of(
                createTransaction("S", "D", 10f, 100f, BASE_DATE.minusDays(1)),
                createTransaction("P", "A", 5f, 80f, BASE_DATE.minusDays(1))));

        when(form4Repository.findRecentTransactions(any(LocalDate.class))).thenReturn(List.of(form));
        when(companyMarketDataService.getStoredMarketData("AAPL")).thenReturn(Optional.of(CompanyMarketData.builder()
                .ticker("AAPL")
                .currentPrice(90d)
                .marketCap(3_000_000_000_000d)
                .build()));

        PaginatedResponse<InsiderActivityResponse> result = service.screen(InsiderActivityScreenRequest.builder()
                .preset("LATEST_SALES")
                .page(0)
                .size(50)
                .build());

        assertEquals(1, result.getTotalElements());
        InsiderActivityResponse row = result.getContent().get(0);
        assertEquals("TRANSACTION", row.getView());
        assertEquals("SELL", row.getSide());
        assertEquals("S", row.getTransactionCode());
        assertEquals(1000f, row.getTransactionValue());
    }

    @Test
    @DisplayName("multi-insider buys preset groups by stock and counts distinct owner CIKs")
    void multiInsiderBuysGroupsByStock() {
        Form4 first = createForm4("0002", "MSFT", "Alice Buyer", "0000000001");
        first.setTransactions(List.of(createTransaction("P", "A", 10f, 100f, BASE_DATE.minusMonths(1))));
        Form4 second = createForm4("0003", "MSFT", "Bob Buyer", "0000000002");
        second.setTransactions(List.of(createTransaction("P", "A", 20f, 110f, BASE_DATE.minusMonths(1))));
        Form4 duplicateOwner = createForm4("0004", "MSFT", "Alice Buyer", "0000000001");
        duplicateOwner.setTransactions(List.of(createTransaction("P", "A", 5f, 120f, BASE_DATE.minusMonths(1))));

        when(form4Repository.findRecentTransactions(any(LocalDate.class))).thenReturn(List.of(first, second, duplicateOwner));
        when(companyMarketDataService.getStoredMarketData("MSFT")).thenReturn(Optional.empty());

        PaginatedResponse<InsiderActivityResponse> result = service.screen(InsiderActivityScreenRequest.builder()
                .preset("MULTI_INSIDER_BUYS")
                .page(0)
                .size(50)
                .build());

        assertEquals(1, result.getTotalElements());
        InsiderActivityResponse row = result.getContent().get(0);
        assertEquals("AGGREGATE", row.getView());
        assertEquals(2, row.getInsiderCount());
        assertEquals(3, row.getTransactionCount());
        assertEquals(3800f, row.getTotalValue());
    }

    @Test
    @DisplayName("configurable transaction codes override preset defaults while side still applies")
    void configurableCodesOverridePresetDefaults() {
        Form4 form = createForm4("0005", "AAPL", "Award Buyer", "0000000005");
        form.setTransactions(List.of(
                createTransaction("A", "A", 10f, 50f, BASE_DATE.minusDays(1)),
                createTransaction("S", "D", 10f, 50f, BASE_DATE.minusDays(1))));

        when(form4Repository.findRecentTransactions(any(LocalDate.class))).thenReturn(List.of(form));
        when(companyMarketDataService.getStoredMarketData("AAPL")).thenReturn(Optional.empty());

        PaginatedResponse<InsiderActivityResponse> result = service.screen(InsiderActivityScreenRequest.builder()
                .preset("LATEST_PURCHASES")
                .transactionCodes(Set.of("A,S"))
                .page(0)
                .size(50)
                .build());

        assertEquals(1, result.getTotalElements());
        assertEquals("BUY", result.getContent().get(0).getSide());
        assertEquals("A", result.getContent().get(0).getTransactionCode());
    }

    @Test
    @DisplayName("$1M presets apply threshold to stock aggregate value")
    void millionDollarPresetsApplyAggregateThreshold() {
        Form4 first = createForm4("0006", "NVDA", "Alice Buyer", "0000000006");
        first.setTransactions(List.of(createTransaction("P", "A", 3_000f, 200f, BASE_DATE.minusDays(10))));
        Form4 second = createForm4("0007", "NVDA", "Bob Buyer", "0000000007");
        second.setTransactions(List.of(createTransaction("P", "A", 2_500f, 200f, BASE_DATE.minusDays(9))));

        when(form4Repository.findRecentTransactions(any(LocalDate.class))).thenReturn(List.of(first, second));
        when(companyMarketDataService.getStoredMarketData("NVDA")).thenReturn(Optional.empty());

        PaginatedResponse<InsiderActivityResponse> result = service.screen(InsiderActivityScreenRequest.builder()
                .preset("MILLION_DOLLAR_BUYS")
                .page(0)
                .size(50)
                .build());

        assertEquals(1, result.getTotalElements());
        assertEquals(1_100_000f, result.getContent().get(0).getTotalValue());
    }

    @Test
    @DisplayName("coverage returns per-day filing counts for form 4")
    void coverageReturnsForm4DailyCounts() {
        LocalDate from = BASE_DATE.minusDays(3);
        LocalDate to = BASE_DATE.minusDays(1);
        Form4 first = createForm4("0012", "AAPL", "Alice Buyer", "0000000012", BASE_DATE.minusDays(2));
        Form4 second = createForm4("0013", "MSFT", "Bob Buyer", "0000000013", BASE_DATE.minusDays(1));
        Form4 ignored = createForm4("0014", "MSFT", "Bob Buyer", "0000000013", null);

        when(form4Repository.findByTransactionDateBetween(from, to, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(first, second, ignored)));

        InsiderActivityCoverageResponse coverage = service.coverage("4", from, to);

        assertEquals("4", coverage.getForm());
        assertEquals(from, coverage.getFrom());
        assertEquals(to, coverage.getTo());
        assertEquals(2L, coverage.getTotalFilings());
        assertEquals(2, coverage.getDays().size());
        assertEquals(BASE_DATE.minusDays(2).toString(), coverage.getDays().get(0).getDate().toString());
        assertEquals(1L, coverage.getDays().get(0).getCount());
        assertEquals(BASE_DATE.minusDays(1).toString(), coverage.getDays().get(1).getDate().toString());
        assertEquals(1L, coverage.getDays().get(1).getCount());
    }

    @Test
    @DisplayName("coverage rejects unsupported form")
    void coverageRejectsUnsupportedForm() {
        assertThrows(IllegalArgumentException.class, () -> service.coverage("5", BASE_DATE.minusDays(3), BASE_DATE.minusDays(1)));
    }

    @Test
    @DisplayName("coverage rejects inverted date windows")
    void coverageRejectsInvertedWindow() {
        assertThrows(IllegalArgumentException.class, () -> service.coverage("4", BASE_DATE.minusDays(1), BASE_DATE.minusDays(3)));
    }

    @Test
    @DisplayName("export supports CSV and JSON and applies the active filters")
    void exportSupportsCsvAndJson() {
        Form4 form = createForm4("0008", "AAPL", "Alice Buyer", "0000000008");
        form.setTransactions(List.of(createTransaction("P", "A", 10f, 100f, BASE_DATE.minusDays(1))));

        when(form4Repository.findRecentTransactions(any(LocalDate.class))).thenReturn(List.of(form));
        when(companyMarketDataService.getStoredMarketData("AAPL")).thenReturn(Optional.empty());

        InsiderActivityScreenRequest request = InsiderActivityScreenRequest.builder()
                .preset("LATEST_PURCHASES")
                .build();

        String csv = new String(service.export(request, "CSV"));
        String json = new String(service.export(request, "JSON"));

        assertTrue(csv.contains("ticker"));
        assertTrue(csv.contains("AAPL"));
        assertTrue(json.contains("\"ticker\":\"AAPL\""));
    }

    @Test
    @DisplayName("nested transactions derive missing value instead of inheriting form-level total")
    void nestedTransactionsDeriveMissingValueBeforeFormFallback() {
        Form4 form = createForm4("0009", "AAPL", "Alice Buyer", "0000000009");
        form.setTransactionValue(99_999f);
        form.setTransactions(List.of(createTransactionWithoutValue("P", "A", 10f, 50f, BASE_DATE.minusDays(1))));

        when(form4Repository.findRecentTransactions(any(LocalDate.class))).thenReturn(List.of(form));
        when(companyMarketDataService.getStoredMarketData("AAPL")).thenReturn(Optional.empty());

        PaginatedResponse<InsiderActivityResponse> result = service.screen(InsiderActivityScreenRequest.builder()
                .preset("LATEST_PURCHASES")
                .page(0)
                .size(50)
                .build());

        assertEquals(1, result.getTotalElements());
        assertEquals(500f, result.getContent().get(0).getTransactionValue());
    }

    @Test
    @DisplayName("descending transaction value sort keeps missing values last")
    void descendingTransactionValueSortKeepsMissingValuesLast() {
        Form4 missingValue = createForm4("0010", "MISS", "Missing Value", "0000000010");
        missingValue.setTransactions(List.of(createTransactionWithoutValue("P", "A", 10f, null, BASE_DATE.minusDays(1))));
        Form4 highValue = createForm4("0011", "HIGH", "High Value", "0000000011");
        highValue.setTransactions(List.of(createTransaction("P", "A", 10f, 100f, BASE_DATE.minusDays(1))));

        when(form4Repository.findRecentTransactions(any(LocalDate.class))).thenReturn(List.of(missingValue, highValue));
        when(companyMarketDataService.getStoredMarketData("MISS")).thenReturn(Optional.empty());
        when(companyMarketDataService.getStoredMarketData("HIGH")).thenReturn(Optional.empty());

        PaginatedResponse<InsiderActivityResponse> result = service.screen(InsiderActivityScreenRequest.builder()
                .preset("LATEST_PURCHASES")
                .sortBy("totalValue")
                .sortDir("desc")
                .page(0)
                .size(50)
                .build());

        assertEquals(2, result.getTotalElements());
        assertEquals("HIGH", result.getContent().get(0).getTicker());
        assertEquals("MISS", result.getContent().get(1).getTicker());
    }

    private Form4 createForm4(String accessionNumber, String ticker, String ownerName, String ownerCik) {
        return Form4.builder()
                .accessionNumber(accessionNumber)
                .documentType("4")
                .cik("0000123456")
                .issuerName(ticker + " Inc.")
                .tradingSymbol(ticker)
                .rptOwnerCik(ownerCik)
                .rptOwnerName(ownerName)
                .ownerType("Officer")
                .officerTitle("Chief Executive Officer")
                .transactionDate(BASE_DATE.minusDays(1))
                .acquiredDisposedCode("A")
                .build();
    }

    private Form4 createForm4(
            String accessionNumber,
            String ticker,
            String ownerName,
            String ownerCik,
            LocalDate transactionDate) {
        return Form4.builder()
                .accessionNumber(accessionNumber)
                .documentType("4")
                .cik("0000123456")
                .issuerName(ticker + " Inc.")
                .tradingSymbol(ticker)
                .rptOwnerCik(ownerCik)
                .rptOwnerName(ownerName)
                .ownerType("Officer")
                .officerTitle("Chief Executive Officer")
                .transactionDate(transactionDate)
                .acquiredDisposedCode("A")
                .build();
    }

    private Form4Transaction createTransaction(
            String transactionCode,
            String acquiredDisposedCode,
            float shares,
            float price,
            LocalDate transactionDate) {
        return Form4Transaction.builder()
                .transactionType("NON_DERIVATIVE")
                .transactionCode(transactionCode)
                .acquiredDisposedCode(acquiredDisposedCode)
                .transactionShares(shares)
                .transactionPricePerShare(price)
                .transactionValue(shares * price)
                .transactionDate(transactionDate)
                .build();
    }

    private Form4Transaction createTransactionWithoutValue(
            String transactionCode,
            String acquiredDisposedCode,
            float shares,
            Float price,
            LocalDate transactionDate) {
        return Form4Transaction.builder()
                .transactionType("NON_DERIVATIVE")
                .transactionCode(transactionCode)
                .acquiredDisposedCode(acquiredDisposedCode)
                .transactionShares(shares)
                .transactionPricePerShare(price)
                .transactionDate(transactionDate)
                .build();
    }
}
