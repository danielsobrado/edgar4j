package org.jds.edgar4j.service.analytics;

import org.jds.edgar4j.model.insider.Company;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.service.provider.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InsiderAnalyticsServiceTest {

    @Mock
    private MarketDataService marketDataService;

    private InsiderAnalyticsService analyticsService;

    @BeforeEach
    void setUp() {
        analyticsService = new InsiderAnalyticsService(marketDataService);
    }

    @Test
    @DisplayName("calculateTransactionAnalytics should score market timing from subsequent price movement")
    void calculateTransactionAnalyticsShouldScoreMarketTiming() {
        InsiderTransaction transaction = transaction("P", InsiderTransaction.AcquiredDisposed.ACQUIRED,
            LocalDate.of(2026, 1, 2), "100.00");

        when(marketDataService.getPriceForDate("MSFT", LocalDate.of(2026, 2, 1)))
            .thenReturn(CompletableFuture.completedFuture(new BigDecimal("120.00")));

        InsiderAnalyticsService.TransactionAnalytics analytics =
            analyticsService.calculateTransactionAnalytics(transaction);

        assertEquals(9, analytics.getMarketTimingScore());
        assertEquals("Purchase", analytics.getTransactionClassification());
    }

    @Test
    @DisplayName("calculateInsiderMetrics should use directional post-transaction performance")
    void calculateInsiderMetricsShouldUseDirectionalPerformance() {
        InsiderTransaction winningPurchase = transaction("P", InsiderTransaction.AcquiredDisposed.ACQUIRED,
            LocalDate.of(2026, 1, 2), "100.00");
        InsiderTransaction winningSale = transaction("S", InsiderTransaction.AcquiredDisposed.DISPOSED,
            LocalDate.of(2026, 2, 2), "100.00");
        InsiderTransaction losingSale = transaction("S", InsiderTransaction.AcquiredDisposed.DISPOSED,
            LocalDate.of(2026, 3, 2), "100.00");

        when(marketDataService.getPriceForDate("MSFT", LocalDate.of(2026, 2, 1)))
            .thenReturn(CompletableFuture.completedFuture(new BigDecimal("110.00")));
        when(marketDataService.getPriceForDate("MSFT", LocalDate.of(2026, 3, 4)))
            .thenReturn(CompletableFuture.completedFuture(new BigDecimal("90.00")));
        when(marketDataService.getPriceForDate("MSFT", LocalDate.of(2026, 4, 1)))
            .thenReturn(CompletableFuture.completedFuture(new BigDecimal("105.00")));

        InsiderAnalyticsService.InsiderMetrics metrics = analyticsService.calculateInsiderMetrics(
            "0000123456",
            List.of(winningPurchase, winningSale, losingSale),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 4, 30));

        assertEquals(3, metrics.getTotalTransactions());
        assertEquals("S", metrics.getPreferredTransactionType());
        assertEquals(66.66666666666666, metrics.getSuccessRate(), 0.0001);
        assertEquals(5.0, metrics.getOverallPerformance(), 0.0001);
    }

    private InsiderTransaction transaction(
            String code,
            InsiderTransaction.AcquiredDisposed acquiredDisposed,
            LocalDate transactionDate,
            String price) {
        return InsiderTransaction.builder()
            .company(Company.builder()
                .cik("0000789019")
                .companyName("Microsoft Corporation")
                .tickerSymbol("MSFT")
                .build())
            .accessionNumber("0000789019-26-000001")
            .transactionDate(transactionDate)
            .filingDate(transactionDate.plusDays(2))
            .securityTitle("Common Stock")
            .transactionCode(code)
            .acquiredDisposed(acquiredDisposed)
            .ownershipNature(InsiderTransaction.OwnershipNature.DIRECT)
            .sharesTransacted(new BigDecimal("10"))
            .pricePerShare(new BigDecimal(price))
            .transactionValue(new BigDecimal(price).multiply(new BigDecimal("10")))
            .isDerivative(false)
            .build();
    }
}
