package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jds.edgar4j.dto.response.InsiderPurchaseResponse;
import org.jds.edgar4j.dto.response.InsiderPurchaseSummary;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form4Transaction;
import org.jds.edgar4j.model.MarketCapSource;
import org.jds.edgar4j.repository.Form4Repository;
import org.jds.edgar4j.service.CompanyMarketDataService;
import org.jds.edgar4j.service.Sp500Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InsiderPurchaseServiceImplTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2025, 1, 15);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            BASE_DATE.atStartOfDay(ZoneId.systemDefault()).toInstant(),
            ZoneId.systemDefault());

    @Mock
    private Form4Repository form4Repository;

    @Mock
    private CompanyMarketDataService companyMarketDataService;

    @Mock
    private Sp500Service sp500Service;

    private InsiderPurchaseServiceImpl insiderPurchaseService;

    @BeforeEach
    void setUp() {
        insiderPurchaseService = new InsiderPurchaseServiceImpl(
                form4Repository,
                companyMarketDataService,
                                sp500Service,
                                FIXED_CLOCK);
    }

    @Test
    @DisplayName("getRecentInsiderPurchases should flatten qualifying transactions and apply filters")
    void getRecentInsiderPurchasesShouldFlattenTransactionsAndApplyFilters() {
        Form4 multiTransactionForm = createForm4("0001111111-26-000001", "AAPL", "Apple Inc.", "Officer", "Alice Officer");
        multiTransactionForm.setTransactions(List.of(
                createTransaction("P", "A", 10f, 100f, BASE_DATE.minusDays(5)),
                createTransaction("A", "A", 5f, 80f, BASE_DATE.minusDays(5))));
        multiTransactionForm.setAcquiredDisposedCode("D");
        multiTransactionForm.setTransactionPricePerShare(100f);
        multiTransactionForm.setTransactionShares(10f);

        Form4 nonSp500Form = createForm4("0002222222-26-000002", "OTHR", "Other Co", null, "Bob Buyer");
        nonSp500Form.setTransactions(List.of(createTransaction("P", "A", 15f, 20f, BASE_DATE.minusDays(4))));

        Form4 smallCapForm = createForm4("0003333333-26-000003", "SMAL", "Small Cap", null, "Charlie Buyer");
        smallCapForm.setTransactions(List.of(createTransaction("P", "A", 20f, 10f, BASE_DATE.minusDays(3))));

        when(form4Repository.findRecentAcquisitions(any(LocalDate.class)))
                .thenReturn(List.of(multiTransactionForm, nonSp500Form, smallCapForm));
        when(sp500Service.getAllTickers()).thenReturn(Set.of("AAPL", "MSFT"));
        when(companyMarketDataService.getStoredMarketData("AAPL")).thenReturn(Optional.of(CompanyMarketData.builder()
                .ticker("AAPL")
                .currentPrice(125d)
                .marketCap(3_200_000_000_000d)
                .marketCapSource(MarketCapSource.PROVIDER_MARKET_CAP)
                .build()));
        PaginatedResponse<InsiderPurchaseResponse> result = insiderPurchaseService.getRecentInsiderPurchases(
                30,
                1_000_000_000d,
                true,
                500d,
                "percentChange",
                "desc",
                0,
                50);

        assertEquals(1, result.getTotalElements());
        InsiderPurchaseResponse response = result.getContent().get(0);
        assertEquals("AAPL", response.getTicker());
        assertEquals("Alice Officer", response.getInsiderName());
        assertEquals("Officer", response.getOwnerType());
        assertEquals("P", response.getTransactionCode());
        assertEquals(1000f, response.getTransactionValue());
        assertEquals(25d, response.getPercentChange());
        assertEquals(MarketCapSource.PROVIDER_MARKET_CAP, response.getMarketCapSource());
        assertTrue(response.isSp500());
    }

    @Test
    @DisplayName("getRecentInsiderPurchases should fall back to form-level fields when transaction details are missing")
    void getRecentInsiderPurchasesShouldFallbackToFormLevelData() {
        Form4 fallbackForm = createForm4("0004444444-26-000004", "MSFT", "Microsoft", null, "Dana Director");
        fallbackForm.setDirector(true);
        fallbackForm.setOwnerType(null);
        fallbackForm.setTransactionDate(BASE_DATE.minusDays(2));
        fallbackForm.setTransactionShares(50f);
        fallbackForm.setTransactionPricePerShare(40f);
        fallbackForm.setTransactionValue(2000f);
        fallbackForm.setAcquiredDisposedCode("A");
        fallbackForm.setTransactions(List.of());

        when(form4Repository.findRecentAcquisitions(any(LocalDate.class))).thenReturn(List.of(fallbackForm));
        when(sp500Service.getAllTickers()).thenReturn(Set.of("MSFT"));
        when(companyMarketDataService.getStoredMarketData("MSFT")).thenReturn(Optional.of(CompanyMarketData.builder()
                .ticker("MSFT")
                .currentPrice(44d)
                .marketCap(2_500_000_000_000d)
                .build()));

        InsiderPurchaseResponse response = insiderPurchaseService.getRecentInsiderPurchases(
                30,
                null,
                false,
                null,
                "transactionDate",
                "desc",
                0,
                10)
                .getContent()
                .get(0);

        assertEquals("Director", response.getOwnerType());
        assertEquals("P", response.getTransactionCode());
        assertEquals(10d, response.getPercentChange());
    }

    @Test
    @DisplayName("getSummary should aggregate purchase counts, unique companies, and percent changes")
    void getSummaryShouldAggregateAcrossResponses() {
        Form4 aaplForm = createForm4("0005555555-26-000005", "AAPL", "Apple Inc.", "Officer", "Eve Officer");
        aaplForm.setTransactions(List.of(
                createTransaction("P", "A", 10f, 100f, BASE_DATE.minusDays(10)),
                createTransaction("P", "A", 5f, 90f, BASE_DATE.minusDays(9))));

        Form4 msftForm = createForm4("0006666666-26-000006", "MSFT", "Microsoft", "Director", "Frank Director");
        msftForm.setTransactions(List.of(createTransaction("P", "A", 20f, 50f, BASE_DATE.minusDays(8))));

        Form4 grantOnlyForm = createForm4("0007777777-26-000007", "NVDA", "Nvidia", "Officer", "Grace Grant");
        grantOnlyForm.setTransactions(List.of(createTransaction("A", "A", 10f, 20f, BASE_DATE.minusDays(7))));

        when(form4Repository.findRecentAcquisitions(any(LocalDate.class)))
                .thenReturn(List.of(aaplForm, msftForm, grantOnlyForm));
        when(sp500Service.getAllTickers()).thenReturn(Set.of("AAPL", "MSFT"));
        when(companyMarketDataService.getStoredMarketData("AAPL")).thenReturn(Optional.of(CompanyMarketData.builder()
                .ticker("AAPL")
                .currentPrice(120d)
                .marketCap(3_000_000_000_000d)
                .build()));
        when(companyMarketDataService.getStoredMarketData("MSFT")).thenReturn(Optional.of(CompanyMarketData.builder()
                .ticker("MSFT")
                .currentPrice(45d)
                .marketCap(2_800_000_000_000d)
                .build()));

        InsiderPurchaseSummary summary = insiderPurchaseService.getSummary(30);

        assertNotNull(summary);
        assertEquals(3, summary.getTotalPurchases());
        assertEquals(2, summary.getUniqueCompanies());
        assertEquals(2450d, summary.getTotalPurchaseValue());
        assertEquals(2, summary.getPositiveChangeCount());
        assertEquals(1, summary.getNegativeChangeCount());
        assertEquals(14.44d, summary.getAveragePercentChange());
    }

    @Test
    @DisplayName("getRecentInsiderPurchases should handle missing market data")
    void getRecentInsiderPurchasesShouldHandleMissingMarketData() {
        Form4 form = createForm4("0008888888-26-000008", "OTHR", "No Data Inc.", "Other", "Hank Holder");
        form.setTransactions(List.of(createTransaction("P", "A", 10f, 25f, BASE_DATE.minusDays(1))));

        when(form4Repository.findRecentAcquisitions(any(LocalDate.class))).thenReturn(List.of(form));
        when(sp500Service.getAllTickers()).thenReturn(Set.of());
        when(companyMarketDataService.getStoredMarketData("OTHR")).thenReturn(Optional.empty());

        InsiderPurchaseResponse response = insiderPurchaseService.getRecentInsiderPurchases(
                30,
                null,
                false,
                null,
                "percentChange",
                "desc",
                0,
                10)
                .getContent()
                .get(0);

        assertNull(response.getCurrentPrice());
        assertNull(response.getPercentChange());
        assertEquals(250f, response.getTransactionValue());
        assertNull(response.getMarketCapSource());
    }

    @Test
    @DisplayName("getRecentInsiderPurchases should skip placeholder symbols that are not tradable tickers")
    void getRecentInsiderPurchasesShouldSkipPlaceholderSymbols() {
        Form4 noneForm = createForm4("0008888888-26-000008", "NONE", "No Ticker Inc.", "Other", "Hank Holder");
        noneForm.setTransactions(List.of(createTransaction("P", "A", 10f, 25f, BASE_DATE.minusDays(1))));
        Form4 naForm = createForm4("0008888888-26-000009", "N/A", "No Ticker LLC", "Officer", "Ina Insider");
        naForm.setTransactions(List.of(createTransaction("P", "A", 5f, 30f, BASE_DATE.minusDays(1))));

        when(form4Repository.findRecentAcquisitions(any(LocalDate.class))).thenReturn(List.of(noneForm, naForm));
        when(sp500Service.getAllTickers()).thenReturn(Set.of());

        PaginatedResponse<InsiderPurchaseResponse> result = insiderPurchaseService.getRecentInsiderPurchases(
                30,
                null,
                false,
                null,
                "percentChange",
                "desc",
                0,
                10);

        assertEquals(0, result.getTotalElements());
        assertTrue(result.getContent().isEmpty());
    }

    @Test
    @DisplayName("getRecentInsiderPurchases should keep S&P 500 rows when market cap is missing and the view is already S&P 500-only")
    void getRecentInsiderPurchasesShouldKeepSp500RowsWhenMarketCapMissing() {
        Form4 form = createForm4("0000051434-26-000058", "IP", "International Paper", "Director", "Anders Gustafsson");
        form.setTransactions(List.of(createTransaction("P", "A", 13217f, 37.831f, BASE_DATE.minusDays(1))));

        when(form4Repository.findRecentAcquisitions(any(LocalDate.class))).thenReturn(List.of(form));
        when(sp500Service.getAllTickers()).thenReturn(Set.of("IP"));
        when(companyMarketDataService.getStoredMarketData("IP")).thenReturn(Optional.of(CompanyMarketData.builder()
                .ticker("IP")
                .currentPrice(37.25d)
                .marketCap(null)
                .build()));

        PaginatedResponse<InsiderPurchaseResponse> result = insiderPurchaseService.getRecentInsiderPurchases(
                30,
                1_000_000_000d,
                true,
                null,
                "transactionDate",
                "desc",
                0,
                10);

        assertEquals(1, result.getTotalElements());
        assertEquals("IP", result.getContent().get(0).getTicker());
        assertEquals(37.25d, result.getContent().get(0).getCurrentPrice());
        assertNull(result.getContent().get(0).getMarketCap());
    }

    @Test
    @DisplayName("getRecentInsiderPurchases should include only qualifying transactions within the requested lookback")
    void getRecentInsiderPurchasesShouldFilterNestedTransactionsByDate() {
        Form4 mixedDateForm = createForm4("0009999999-26-000009", "NFLX", "Netflix", "Officer", "Ivy Insider");
        mixedDateForm.setTransactionDate(BASE_DATE.minusDays(2));
        mixedDateForm.setTransactions(List.of(
                createTransaction("P", "A", 10f, 30f, BASE_DATE.minusDays(40)),
                createTransaction("P", "A", 5f, 35f, BASE_DATE.minusDays(2))));

        when(form4Repository.findRecentAcquisitions(any(LocalDate.class))).thenReturn(List.of(mixedDateForm));
        when(sp500Service.getAllTickers()).thenReturn(Set.of());
        when(companyMarketDataService.getStoredMarketData("NFLX")).thenReturn(Optional.of(CompanyMarketData.builder()
                .ticker("NFLX")
                .currentPrice(40d)
                .marketCap(400_000_000_000d)
                .build()));

        PaginatedResponse<InsiderPurchaseResponse> result = insiderPurchaseService.getRecentInsiderPurchases(
                30,
                null,
                false,
                null,
                "transactionDate",
                "desc",
                0,
                10);

        assertEquals(1, result.getTotalElements());
        InsiderPurchaseResponse response = result.getContent().get(0);
        assertEquals(BASE_DATE.minusDays(2), response.getTransactionDate());
        assertEquals(175f, response.getTransactionValue());
    }

        @Test
        @DisplayName("getRecentInsiderPurchases should use stored market data without triggering refresh-on-read lookups")
        void getRecentInsiderPurchasesShouldUseStoredMarketDataOnly() {
                Form4 form = createForm4("0001111111-26-000010", "AAPL", "Apple Inc.", "Officer", "Alice Officer");
                form.setTransactions(List.of(createTransaction("P", "A", 10f, 100f, BASE_DATE.minusDays(1))));

                when(form4Repository.findRecentAcquisitions(any(LocalDate.class))).thenReturn(List.of(form));
                when(sp500Service.getAllTickers()).thenReturn(Set.of("AAPL"));
                when(companyMarketDataService.getStoredMarketData("AAPL")).thenReturn(Optional.of(CompanyMarketData.builder()
                                .ticker("AAPL")
                                .currentPrice(110d)
                                .marketCap(3_000_000_000_000d)
                                .build()));

                PaginatedResponse<InsiderPurchaseResponse> result = insiderPurchaseService.getRecentInsiderPurchases(
                                30,
                                null,
                                false,
                                null,
                                "transactionDate",
                                "desc",
                                0,
                                10);

                assertEquals(1, result.getTotalElements());
                verify(companyMarketDataService).getStoredMarketData("AAPL");
                verify(companyMarketDataService, never()).getMarketData("AAPL");
        }

    private Form4 createForm4(
            String accessionNumber,
            String ticker,
            String issuerName,
            String ownerType,
            String ownerName) {
        return Form4.builder()
                .accessionNumber(accessionNumber)
                .documentType("4")
                .cik("0000123456")
                .issuerName(issuerName)
                .tradingSymbol(ticker)
                .rptOwnerCik("0000000001")
                .rptOwnerName(ownerName)
                .ownerType(ownerType)
                .officerTitle("Chief Executive Officer")
                .isOfficer(true)
                .transactionDate(BASE_DATE.minusDays(5))
                .transactionShares(10f)
                .transactionPricePerShare(100f)
                .transactionValue(1000f)
                .acquiredDisposedCode("A")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
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
}
