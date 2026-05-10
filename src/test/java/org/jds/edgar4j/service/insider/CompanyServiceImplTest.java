package org.jds.edgar4j.service.insider;

import org.jds.edgar4j.model.insider.Company;
import org.jds.edgar4j.model.insider.Insider;
import org.jds.edgar4j.model.insider.InsiderTransaction;
import org.jds.edgar4j.port.InsiderCompanyDataPort;
import org.jds.edgar4j.port.InsiderCompanyRelationshipDataPort;
import org.jds.edgar4j.port.InsiderTransactionDataPort;
import org.jds.edgar4j.service.insider.impl.CompanyServiceImpl;
import org.jds.edgar4j.service.provider.MarketDataProvider;
import org.jds.edgar4j.service.provider.MarketDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyServiceImplTest {

    @Mock
    private InsiderCompanyDataPort companyRepository;

    @Mock
    private InsiderTransactionDataPort transactionRepository;

    @Mock
    private InsiderCompanyRelationshipDataPort relationshipRepository;

    @Mock
    private MarketDataService marketDataService;

    private CompanyServiceImpl companyService;

    @BeforeEach
    void setUp() {
        companyService = new CompanyServiceImpl(
            companyRepository,
            transactionRepository,
            relationshipRepository,
            marketDataService);
    }

    @Test
    @DisplayName("enrichCompanyData should persist market data from provider responses")
    void enrichCompanyDataShouldPersistMarketData() {
        Company company = Company.builder()
            .cik("0000789019")
            .companyName("Old Microsoft")
            .tickerSymbol("MSFT")
            .totalSharesOutstanding(new BigDecimal("100"))
            .build();

        MarketDataProvider.StockPrice stockPrice = new MarketDataProvider.StockPrice();
        stockPrice.setPrice(new BigDecimal("12.50"));
        stockPrice.setVolume(1_000_000L);

        MarketDataProvider.CompanyProfile profile = new MarketDataProvider.CompanyProfile();
        profile.setName("Microsoft Corporation");
        profile.setSector("Technology");
        profile.setIndustry("Software");
        profile.setCountry("US");
        profile.setExchange("NASDAQ");
        profile.setWebsite("https://www.microsoft.com");
        profile.setSharesOutstanding(200L);
        profile.setMarketCapitalization(2_500L);

        MarketDataProvider.FinancialMetrics metrics = new MarketDataProvider.FinancialMetrics();
        metrics.setPeRatio(new BigDecimal("32.10"));
        metrics.setPriceToBook(new BigDecimal("11.25"));
        metrics.setDividendYield(new BigDecimal("0.008"));

        when(marketDataService.getEnhancedMarketData("MSFT")).thenReturn(CompletableFuture.completedFuture(
            new MarketDataService.EnhancedMarketData("MSFT", stockPrice, profile, metrics)));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Company enrichedCompany = companyService.enrichCompanyData(company);

        assertSame(company, enrichedCompany);
        assertEquals("Microsoft Corporation", enrichedCompany.getCompanyName());
        assertEquals("Technology", enrichedCompany.getSector());
        assertEquals("Software", enrichedCompany.getIndustry());
        assertEquals("NASDAQ", enrichedCompany.getExchange());
        assertEquals(new BigDecimal("12.50"), enrichedCompany.getCurrentStockPrice());
        assertEquals(new BigDecimal("2500"), enrichedCompany.getMarketCap());
        assertEquals(new BigDecimal("32.10"), enrichedCompany.getPeRatio());
        verify(companyRepository).save(company);
    }

    @Test
    @DisplayName("updateMarketCap should use formatted CIK and save current market cap")
    void updateMarketCapShouldUseFormattedCikAndSaveMarketCap() {
        Company company = Company.builder()
            .cik("0000789019")
            .companyName("Microsoft Corporation")
            .tickerSymbol("MSFT")
            .totalSharesOutstanding(new BigDecimal("100"))
            .build();
        MarketDataProvider.StockPrice stockPrice = new MarketDataProvider.StockPrice();
        stockPrice.setPrice(new BigDecimal("10.00"));
        stockPrice.setVolume(500L);

        when(companyRepository.findByCik("0000789019")).thenReturn(Optional.of(company));
        when(marketDataService.getCurrentPrice("MSFT")).thenReturn(CompletableFuture.completedFuture(stockPrice));
        when(companyRepository.save(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));

        companyService.updateMarketCap("789019");

        ArgumentCaptor<Company> companyCaptor = ArgumentCaptor.forClass(Company.class);
        verify(companyRepository).save(companyCaptor.capture());
        assertEquals(new BigDecimal("10.00"), companyCaptor.getValue().getCurrentStockPrice());
        assertEquals(new BigDecimal("1000.00"), companyCaptor.getValue().getMarketCap());
        assertEquals(500L, companyCaptor.getValue().getLastTradingVolume());
    }

    @Test
    @DisplayName("getCompanyStatistics should query dependent ports with formatted CIK")
    void getCompanyStatisticsShouldUseFormattedCik() {
        Company company = Company.builder()
            .cik("0000789019")
            .companyName("Microsoft Corporation")
            .tickerSymbol("MSFT")
            .build();
        Insider insider = Insider.builder()
            .cik("0000000001")
            .fullName("Jane Smith")
            .build();
        InsiderTransaction transaction = InsiderTransaction.builder()
            .insider(insider)
            .company(company)
            .transactionDate(LocalDate.of(2026, 1, 2))
            .filingDate(LocalDate.of(2026, 1, 3))
            .transactionCode("P")
            .build();

        when(companyRepository.findByCik("0000789019")).thenReturn(Optional.of(company));
        when(transactionRepository.countTransactionsByCompany("0000789019")).thenReturn(4L);
        when(relationshipRepository.countActiveRelationshipsByCompany("0000789019")).thenReturn(2L);
        when(transactionRepository.findByCompanyCik("0000789019")).thenReturn(List.of(transaction));

        CompanyService.CompanyStatistics statistics = companyService.getCompanyStatistics("789019");

        assertEquals(1L, statistics.getTotalInsiders());
        assertEquals(4L, statistics.getTotalTransactions());
        assertEquals(2L, statistics.getActiveRelationships());
    }
}
