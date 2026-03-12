package org.jds.edgar4j.service.provider;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Abstract interface for market data providers
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
public interface MarketDataProvider {

    /**
     * Get provider name
     */
    String getProviderName();

    /**
     * Get provider priority (lower number = higher priority)
     */
    int getPriority();

    /**
     * Check if provider is available
     */
    boolean isAvailable();

    /**
     * Get current stock price
     */
    CompletableFuture<StockPrice> getCurrentPrice(String symbol);

    /**
     * Get historical stock prices
     */
    CompletableFuture<List<StockPrice>> getHistoricalPrices(String symbol, LocalDate startDate, LocalDate endDate);

    /**
     * Get company profile data
     */
    CompletableFuture<CompanyProfile> getCompanyProfile(String symbol);

    /**
     * Get financial metrics
     */
    CompletableFuture<FinancialMetrics> getFinancialMetrics(String symbol);

    /**
     * Stock price data transfer object
     */
    class StockPrice {
        private String symbol;
        private BigDecimal price;
        private BigDecimal open;
        private BigDecimal high;
        private BigDecimal low;
        private BigDecimal close;
        private Long volume;
        private LocalDate date;
        private String currency;
        private String exchange;

        public StockPrice() {}

        public StockPrice(String symbol, BigDecimal price, LocalDate date) {
            this.symbol = symbol;
            this.price = price;
            this.date = date;
        }

        // Getters and setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }

        public BigDecimal getPrice() { return price; }
        public void setPrice(BigDecimal price) { this.price = price; }

        public BigDecimal getOpen() { return open; }
        public void setOpen(BigDecimal open) { this.open = open; }

        public BigDecimal getHigh() { return high; }
        public void setHigh(BigDecimal high) { this.high = high; }

        public BigDecimal getLow() { return low; }
        public void setLow(BigDecimal low) { this.low = low; }

        public BigDecimal getClose() { return close; }
        public void setClose(BigDecimal close) { this.close = close; }

        public Long getVolume() { return volume; }
        public void setVolume(Long volume) { this.volume = volume; }

        public LocalDate getDate() { return date; }
        public void setDate(LocalDate date) { this.date = date; }

        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }

        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }
    }

    /**
     * Company profile data transfer object
     */
    class CompanyProfile {
        private String symbol;
        private String name;
        private String description;
        private String industry;
        private String sector;
        private String country;
        private String currency;
        private String exchange;
        private Long marketCapitalization;
        private Long sharesOutstanding;
        private String website;
        private String logo;

        public CompanyProfile() {}

        // Getters and setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getIndustry() { return industry; }
        public void setIndustry(String industry) { this.industry = industry; }

        public String getSector() { return sector; }
        public void setSector(String sector) { this.sector = sector; }

        public String getCountry() { return country; }
        public void setCountry(String country) { this.country = country; }

        public String getCurrency() { return currency; }
        public void setCurrency(String currency) { this.currency = currency; }

        public String getExchange() { return exchange; }
        public void setExchange(String exchange) { this.exchange = exchange; }

        public Long getMarketCapitalization() { return marketCapitalization; }
        public void setMarketCapitalization(Long marketCapitalization) { this.marketCapitalization = marketCapitalization; }

        public Long getSharesOutstanding() { return sharesOutstanding; }
        public void setSharesOutstanding(Long sharesOutstanding) { this.sharesOutstanding = sharesOutstanding; }

        public String getWebsite() { return website; }
        public void setWebsite(String website) { this.website = website; }

        public String getLogo() { return logo; }
        public void setLogo(String logo) { this.logo = logo; }
    }

    /**
     * Financial metrics data transfer object
     */
    class FinancialMetrics {
        private String symbol;
        private BigDecimal peRatio;
        private BigDecimal pegRatio;
        private BigDecimal priceToBook;
        private BigDecimal priceToSales;
        private BigDecimal enterpriseValue;
        private BigDecimal evToRevenue;
        private BigDecimal evToEbitda;
        private BigDecimal profitMargin;
        private BigDecimal operatingMargin;
        private BigDecimal returnOnEquity;
        private BigDecimal returnOnAssets;
        private BigDecimal revenueGrowth;
        private BigDecimal earningsGrowth;
        private BigDecimal dividendYield;
        private BigDecimal beta;
        private BigDecimal fiftyTwoWeekHigh;
        private BigDecimal fiftyTwoWeekLow;

        public FinancialMetrics() {}

        // Getters and setters
        public String getSymbol() { return symbol; }
        public void setSymbol(String symbol) { this.symbol = symbol; }

        public BigDecimal getPeRatio() { return peRatio; }
        public void setPeRatio(BigDecimal peRatio) { this.peRatio = peRatio; }

        public BigDecimal getPegRatio() { return pegRatio; }
        public void setPegRatio(BigDecimal pegRatio) { this.pegRatio = pegRatio; }

        public BigDecimal getPriceToBook() { return priceToBook; }
        public void setPriceToBook(BigDecimal priceToBook) { this.priceToBook = priceToBook; }

        public BigDecimal getPriceToSales() { return priceToSales; }
        public void setPriceToSales(BigDecimal priceToSales) { this.priceToSales = priceToSales; }

        public BigDecimal getEnterpriseValue() { return enterpriseValue; }
        public void setEnterpriseValue(BigDecimal enterpriseValue) { this.enterpriseValue = enterpriseValue; }

        public BigDecimal getEvToRevenue() { return evToRevenue; }
        public void setEvToRevenue(BigDecimal evToRevenue) { this.evToRevenue = evToRevenue; }

        public BigDecimal getEvToEbitda() { return evToEbitda; }
        public void setEvToEbitda(BigDecimal evToEbitda) { this.evToEbitda = evToEbitda; }

        public BigDecimal getProfitMargin() { return profitMargin; }
        public void setProfitMargin(BigDecimal profitMargin) { this.profitMargin = profitMargin; }

        public BigDecimal getOperatingMargin() { return operatingMargin; }
        public void setOperatingMargin(BigDecimal operatingMargin) { this.operatingMargin = operatingMargin; }

        public BigDecimal getReturnOnEquity() { return returnOnEquity; }
        public void setReturnOnEquity(BigDecimal returnOnEquity) { this.returnOnEquity = returnOnEquity; }

        public BigDecimal getReturnOnAssets() { return returnOnAssets; }
        public void setReturnOnAssets(BigDecimal returnOnAssets) { this.returnOnAssets = returnOnAssets; }

        public BigDecimal getRevenueGrowth() { return revenueGrowth; }
        public void setRevenueGrowth(BigDecimal revenueGrowth) { this.revenueGrowth = revenueGrowth; }

        public BigDecimal getEarningsGrowth() { return earningsGrowth; }
        public void setEarningsGrowth(BigDecimal earningsGrowth) { this.earningsGrowth = earningsGrowth; }

        public BigDecimal getDividendYield() { return dividendYield; }
        public void setDividendYield(BigDecimal dividendYield) { this.dividendYield = dividendYield; }

        public BigDecimal getBeta() { return beta; }
        public void setBeta(BigDecimal beta) { this.beta = beta; }

        public BigDecimal getFiftyTwoWeekHigh() { return fiftyTwoWeekHigh; }
        public void setFiftyTwoWeekHigh(BigDecimal fiftyTwoWeekHigh) { this.fiftyTwoWeekHigh = fiftyTwoWeekHigh; }

        public BigDecimal getFiftyTwoWeekLow() { return fiftyTwoWeekLow; }
        public void setFiftyTwoWeekLow(BigDecimal fiftyTwoWeekLow) { this.fiftyTwoWeekLow = fiftyTwoWeekLow; }
    }
}
