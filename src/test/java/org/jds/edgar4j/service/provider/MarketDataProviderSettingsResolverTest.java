package org.jds.edgar4j.service.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.jds.edgar4j.config.TiingoEnvProperties;
import org.jds.edgar4j.model.AppSettings;
import org.jds.edgar4j.properties.MarketDataProviderProperties;
import org.jds.edgar4j.port.AppSettingsDataPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MarketDataProviderSettingsResolverTest {

    @Mock
    private AppSettingsDataPort appSettingsRepository;

    @Mock
    private TiingoEnvProperties tiingoEnvProperties;

    private MarketDataProviderProperties properties;
    private MarketDataProviderSettingsResolver resolver;

    @BeforeEach
    void setUp() {
        properties = new MarketDataProviderProperties();
        resolver = new MarketDataProviderSettingsResolver(appSettingsRepository, properties, tiingoEnvProperties);
    }

    @Test
    @DisplayName("resolveSelectedProvider should honor an explicit NONE selection")
    void resolveSelectedProviderShouldHonorExplicitNone() {
        String selectedProvider = resolver.resolveSelectedProvider(AppSettings.builder()
                .marketDataProvider("NONE")
                .build());

        assertEquals(MarketDataProviders.NONE, selectedProvider);
    }

    @Test
    @DisplayName("resolveSelectedProvider should fall back to Tiingo when no explicit provider is stored")
    void resolveSelectedProviderShouldFallbackToTiingoWhenNoExplicitProviderIsStored() {
        when(tiingoEnvProperties.hasApiToken()).thenReturn(true);

        String selectedProvider = resolver.resolveSelectedProvider(AppSettings.builder().build());

        assertEquals(MarketDataProviders.TIINGO, selectedProvider);
    }

    @Test
    @DisplayName("resolve should prefer stored provider settings over legacy selected-provider fields and property defaults")
    void resolveShouldPreferStoredProviderSettings() {
        properties.getFinnhub().setEnabled(false);
        properties.getFinnhub().setBaseUrl("https://property.example/api");
        properties.getFinnhub().setApiKey("property-key");

        AppSettings settings = AppSettings.builder()
                .marketDataProvider("FINNHUB")
                .marketDataBaseUrl("https://legacy.example/api")
                .marketDataApiKey("legacy-key")
                .marketDataProviders(AppSettings.MarketDataProvidersSettings.builder()
                        .finnhub(AppSettings.ProviderSettings.builder()
                                .enabled(true)
                                .baseUrl("https://stored.example/api")
                                .apiKey("stored-key")
                                .build())
                        .build())
                .build();

        MarketDataProviderSettingsResolver.ResolvedProviderConfig resolved = resolver.resolve(
                MarketDataProviders.FINNHUB,
                settings);

        assertTrue(resolved.selected());
        assertTrue(resolved.enabled());
        assertTrue(resolved.configured());
        assertEquals("https://stored.example/api", resolved.baseUrl());
        assertEquals("stored-key", resolved.apiKey());
        assertNotNull(resolved.rateLimit());
    }

    @Test
    @DisplayName("resolve should fall back to provider-config credentials when AppSettings does not store them")
    void resolveShouldFallbackToProviderConfigCredentials() {
        properties.getFinnhub().setEnabled(true);
        properties.getFinnhub().setBaseUrl("https://property.example/api");
        properties.getFinnhub().setApiKey("property-key");

        MarketDataProviderSettingsResolver.ResolvedProviderConfig resolved = resolver.resolve(
                MarketDataProviders.FINNHUB,
                AppSettings.builder()
                        .marketDataProvider("FINNHUB")
                        .marketDataProviders(AppSettings.MarketDataProvidersSettings.builder()
                                .finnhub(AppSettings.ProviderSettings.builder()
                                        .enabled(true)
                                        .build())
                                .build())
                        .build());

        assertTrue(resolved.enabled());
        assertTrue(resolved.configured());
        assertEquals("https://property.example/api", resolved.baseUrl());
        assertEquals("property-key", resolved.apiKey());
    }
}
