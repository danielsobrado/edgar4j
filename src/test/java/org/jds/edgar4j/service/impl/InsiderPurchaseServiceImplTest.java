package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jds.edgar4j.dto.response.InsiderPurchaseResponse;
import org.jds.edgar4j.dto.response.InsiderPurchaseSummary;
import org.jds.edgar4j.model.CompanyMarketData;
import org.jds.edgar4j.model.Form4;
import org.jds.edgar4j.model.Form4Transaction;
import org.jds.edgar4j.repository.Form4Repository;
import org.jds.edgar4j.service.CompanyMarketDataService;
import org.jds.edgar4j.service.Sp500Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;

@ExtendWith(MockitoExtension.class)
class InsiderPurchaseServiceImplTest {

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
                sp500Service);
    }

    @Test
    @DisplayName("getRecentInsiderPurchases should flatten qualifying transactions and apply filters")
    void getRecentInsiderPurchasesShouldFlattenTransactionsAndApplyFilters() {
        Form4 multiTransactionForm = createForm4("0001111111-26-000001", "AAPL", "Apple Inc.", "Officer", "Alice Officer");
        multiTransactionForm.setTransactions(List.of(
                createTransaction("P", "A", 10f, 100f, LocalDate.now().minusDays(5)),
                createTransaction("A", "A", 5f, 80f, LocalDate.now().minusDays(5))));
        multiTransactionForm.setAcquiredDisposedCode("D");
        multiTransactionForm.setTransactionPricePerShare(100f);
        multiTransactionForm.setTransactionShares(10f);

        Form4 nonSp500Form = createForm4("0002222222-26-000002", "OTHR", "Other Co", null, "Bob Buyer");
        nonSp500Form.setTransactions(List.of(createTransaction("P", "A", 15f, 20f, LocalDate.now().minusDays(4))));

        Form4 smallCapForm = createForm4("0003333333-26-000003", "SMAL", "Small Cap", null, "Charlie Buyer");
        smallCapForm.setTransactions(List.of(createTransaction("P", "A", 20f, 10f, LocalDate.now().minusDays(3))));

        when(form4Repository.findRecentAcquisitions(any(LocalDate.class)))
                .thenReturn(List.of(multiTransactionForm, nonSp500Form, smallCapForm));
        when(sp500Service.getAllTickers()).thenReturn(Set.of("AAPL", "MSFT"));
        when(companyMarketDataService.getMarketData("AAPL")).thenReturn(Optional.of(CompanyMarketData.builder()
                .ticker("AAPL")
                .currentPrice(125d)
                .marketCap(3_200_000_000_000d)
                .build()));
        Page<InsiderPurchaseResponse> result = insiderPurchaseService.getRecentInsiderPurchases(
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
        assertTrue(response.isSp500());
    }

    @Test
    @DisplayName("getRecentInsiderPurchases should fall back to form-level fields when transaction details are missing")
    void getRecentInsiderPurchasesShouldFallbackToFormLevelData() {
        Form4 fallbackForm = createForm4("0004444444-26-000004", "MSFT", "Microsoft", null, "Dana Director");
        fallbackForm.setDirector(true);
        fallbackForm.setOwnerType(null);
        fallbackForm.setTransactionDate(LocalDate.now().minusDays(2));
        fallbackForm.setTransactionShares(50f);
        fallbackForm.setTransactionPricePerShare(40f);
        fallbackForm.setTransactionValue(2000f);
        fallbackForm.setAcquiredDisposedCode("A");
        fallbackForm.setTransactions(List.of());

        when(form4Repository.findRecentAcquisitions(any(LocalDate.class))).thenReturn(List.of(fallbackForm));
        when(sp500Service.getAllTickers()).thenReturn(Set.of("MSFT"));
        when(companyMarketDataService.getMarketData("MSFT")).thenReturn(Optional.of(CompanyMarketData.builder()
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
                createTransaction("P", "A", 10f, 100f, LocalDate.now().minusDays(10)),
                createTransaction("P", "A", 5f, 90f, LocalDate.now().minusDays(9))));

        Form4 msftForm = createForm4("0006666666-26-000006", "MSFT", "Microsoft", "Director", "Frank Director");
        msftForm.setTransactions(List.of(createTransaction("P", "A", 20f, 50f, LocalDate.now().minusDays(8))));

        Form4 grantOnlyForm = createForm4("0007777777-26-000007", "NVDA", "Nvidia", "Officer", "Grace Grant");
        grantOnlyForm.setTransactions(List.of(createTransaction("A", "A", 10f, 20f, LocalDate.now().minusDays(7))));

        when(form4Repository.findRecentAcquisitions(any(LocalDate.class)))
                .thenReturn(List.of(aaplForm, msftForm, grantOnlyForm));
        when(sp500Service.getAllTickers()).thenReturn(Set.of("AAPL", "MSFT"));
        when(companyMarketDataService.getMarketData("AAPL")).thenReturn(Optional.of(CompanyMarketData.builder()
                .ticker("AAPL")
                .currentPrice(120d)
                .marketCap(3_000_000_000_000d)
                .build()));
        when(companyMarketDataService.getMarketData("MSFT")).thenReturn(Optional.of(CompanyMarketData.builder()
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
        Form4 form = createForm4("0008888888-26-000008", "NONE", "No Data Inc.", "Other", "Hank Holder");
        form.setTransactions(List.of(createTransaction("P", "A", 10f, 25f, LocalDate.now().minusDays(1))));

        when(form4Repository.findRecentAcquisitions(any(LocalDate.class))).thenReturn(List.of(form));
        when(sp500Service.getAllTickers()).thenReturn(Set.of());
        when(companyMarketDataService.getMarketData("NONE")).thenReturn(Optional.empty());

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
                .transactionDate(LocalDate.now().minusDays(5))
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
