package org.jds.edgar4j.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.bson.Document;
import org.jds.edgar4j.config.TiingoEnvProperties;
import org.jds.edgar4j.dto.request.SettingsRequest;
import org.jds.edgar4j.dto.response.SettingsResponse;
import org.jds.edgar4j.model.AppSettings;
import org.jds.edgar4j.properties.MarketDataProviderProperties;
import org.jds.edgar4j.repository.AppSettingsRepository;
import org.jds.edgar4j.service.provider.MarketDataProviderSettingsResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class SettingsServiceImplTest {

    @Mock
    private AppSettingsRepository appSettingsRepository;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private MongoTemplate mongoTemplate;

    @Mock
    private TiingoEnvProperties tiingoEnvProperties;

    private MarketDataProviderProperties marketDataProviderProperties;
    private MarketDataProviderSettingsResolver marketDataProviderSettingsResolver;
    private SettingsServiceImpl settingsService;

    @BeforeEach
    void setUp() {
        marketDataProviderProperties = new MarketDataProviderProperties();
        marketDataProviderSettingsResolver = new MarketDataProviderSettingsResolver(
                appSettingsRepository,
                marketDataProviderProperties,
                tiingoEnvProperties);
        settingsService = new SettingsServiceImpl(
                appSettingsRepository,
                mongoTemplate,
                tiingoEnvProperties,
                marketDataProviderProperties,
                marketDataProviderSettingsResolver);
        ReflectionTestUtils.setField(settingsService, "baseSecUrl", "https://www.sec.gov");
        ReflectionTestUtils.setField(settingsService, "submissionsUrl", "https://data.sec.gov/submissions");
        ReflectionTestUtils.setField(settingsService, "edgarArchivesUrl", "https://www.sec.gov/Archives/edgar/data");
        ReflectionTestUtils.setField(settingsService, "companyTickersUrl", "https://www.sec.gov/files/company_tickers.json");
        when(mongoTemplate.getDb().runCommand(any(Document.class))).thenReturn(new Document("ok", 1));
        lenient().when(tiingoEnvProperties.hasApiToken()).thenReturn(false);
        lenient().when(tiingoEnvProperties.getApiToken()).thenReturn(Optional.empty());
        lenient().when(tiingoEnvProperties.getBaseUrl()).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("getSettings should redact secrets while exposing whether they are configured")
    void getSettingsShouldRedactSecrets() {
        when(appSettingsRepository.findById("default")).thenReturn(Optional.of(AppSettings.builder()
                .id("default")
                .userAgent("Edgar4j/1.0 (ops@example.com)")
                .smtpPassword("smtp-secret")
                .marketDataProvider("TIINGO")
                .marketDataProviders(AppSettings.MarketDataProvidersSettings.builder()
                        .tiingo(AppSettings.ProviderSettings.builder()
                                .enabled(true)
                                .baseUrl("https://api.tiingo.com")
                                .apiKey("tiingo-secret")
                                .build())
                        .build())
                .marketDataApiKey("tiingo-secret")
                .build()));

        SettingsResponse response = settingsService.getSettings();

        assertNull(response.getSmtpPassword());
        assertTrue(response.isSmtpPasswordConfigured());
        assertNull(response.getMarketDataApiKey());
        assertTrue(response.isMarketDataApiKeyConfigured());
        assertTrue(response.isMarketDataConfigured());
        assertTrue(response.getMarketDataProviders().getTiingo().isApiKeyConfigured());
        assertTrue(response.getMarketDataProviders().getTiingo().isConfigured());
    }

    @Test
    @DisplayName("updateSettings should preserve stored secrets when the request omits them")
    void updateSettingsShouldPreserveStoredSecrets() {
        AppSettings existingSettings = AppSettings.builder()
                .id("default")
                .userAgent("Edgar4j/1.0 (ops@example.com)")
                .emailNotifications(true)
                .notificationEmailTo("alerts@example.com")
                .notificationEmailFrom("noreply@example.com")
                .smtpHost("smtp.example.com")
                .smtpPort(587)
                .smtpUsername("mailer")
                .smtpPassword("smtp-secret")
                .marketDataProvider("TIINGO")
                .marketDataProviders(AppSettings.MarketDataProvidersSettings.builder()
                        .tiingo(AppSettings.ProviderSettings.builder()
                                .enabled(true)
                                .baseUrl("https://api.tiingo.com")
                                .apiKey("tiingo-secret")
                                .build())
                        .build())
                .marketDataApiKey("tiingo-secret")
                .build();
        when(appSettingsRepository.findById("default")).thenReturn(Optional.of(existingSettings));
        when(appSettingsRepository.save(any(AppSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SettingsRequest request = SettingsRequest.builder()
                .userAgent("Edgar4j/1.1 (ops@example.com)")
                .autoRefresh(true)
                .refreshInterval(300)
                .darkMode(false)
                .emailNotifications(true)
                .notificationEmailTo("alerts@example.com")
                .notificationEmailFrom("noreply@example.com")
                .smtpHost("smtp.example.com")
                .smtpPort(587)
                .smtpUsername("mailer")
                .smtpPassword(null)
                .smtpStartTlsEnabled(true)
                .marketDataProvider("TIINGO")
                .marketDataBaseUrl("https://api.tiingo.com")
                .marketDataApiKey(null)
                .clearSmtpPassword(false)
                .clearMarketDataApiKey(false)
                .realtimeSyncEnabled(true)
                .realtimeSyncForms("4")
                .realtimeSyncLookbackHours(1)
                .realtimeSyncMaxPages(10)
                .realtimeSyncPageSize(100)
                .build();

        SettingsResponse response = settingsService.updateSettings(request);

        ArgumentCaptor<AppSettings> savedCaptor = ArgumentCaptor.forClass(AppSettings.class);
        verify(appSettingsRepository).save(savedCaptor.capture());

        assertEquals("smtp-secret", savedCaptor.getValue().getSmtpPassword());
        assertEquals("tiingo-secret", savedCaptor.getValue().getMarketDataApiKey());
        assertNull(response.getSmtpPassword());
        assertNull(response.getMarketDataApiKey());
        assertTrue(response.isSmtpPasswordConfigured());
        assertTrue(response.isMarketDataApiKeyConfigured());
    }

    @Test
    @DisplayName("updateSettings should persist provider-specific credentials without overwriting other providers")
    void updateSettingsShouldPersistProviderSpecificCredentials() {
        AppSettings existingSettings = AppSettings.builder()
                .id("default")
                .marketDataProvider("TIINGO")
                .marketDataProviders(AppSettings.MarketDataProvidersSettings.builder()
                        .tiingo(AppSettings.ProviderSettings.builder()
                                .enabled(true)
                                .baseUrl("https://api.tiingo.com")
                                .apiKey("tiingo-secret")
                                .build())
                        .finnhub(AppSettings.ProviderSettings.builder()
                                .enabled(false)
                                .baseUrl("https://finnhub.io/api/v1")
                                .build())
                        .build())
                .marketDataApiKey("tiingo-secret")
                .marketDataBaseUrl("https://api.tiingo.com")
                .build();
        when(appSettingsRepository.findById("default")).thenReturn(Optional.of(existingSettings));
        when(appSettingsRepository.save(any(AppSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SettingsRequest request = SettingsRequest.builder()
                .userAgent("Edgar4j/1.0 (ops@example.com)")
                .autoRefresh(true)
                .refreshInterval(300)
                .darkMode(false)
                .emailNotifications(false)
                .smtpPort(587)
                .smtpStartTlsEnabled(true)
                .marketDataProvider("FINNHUB")
                .marketDataProviders(SettingsRequest.MarketDataProvidersRequest.builder()
                        .finnhub(SettingsRequest.ProviderRequest.builder()
                                .enabled(true)
                                .baseUrl("https://finnhub.io/api/v1")
                                .apiKey("finnhub-secret")
                                .build())
                        .build())
                .realtimeSyncEnabled(true)
                .realtimeSyncForms("4")
                .realtimeSyncLookbackHours(1)
                .realtimeSyncMaxPages(10)
                .realtimeSyncPageSize(100)
                .build();

        SettingsResponse response = settingsService.updateSettings(request);

        ArgumentCaptor<AppSettings> savedCaptor = ArgumentCaptor.forClass(AppSettings.class);
        verify(appSettingsRepository).save(savedCaptor.capture());

        assertEquals("tiingo-secret", savedCaptor.getValue().getMarketDataProviders().getTiingo().getApiKey());
        assertEquals("finnhub-secret", savedCaptor.getValue().getMarketDataProviders().getFinnhub().getApiKey());
        assertEquals("finnhub-secret", savedCaptor.getValue().getMarketDataApiKey());
        assertEquals("FINNHUB", response.getMarketDataProvider());
        assertTrue(response.getMarketDataProviders().getTiingo().isApiKeyConfigured());
        assertTrue(response.getMarketDataProviders().getFinnhub().isApiKeyConfigured());
        assertTrue(response.getMarketDataProviders().getFinnhub().isConfigured());
    }

    @Test
    @DisplayName("updateSettings should accept provider-service backed market data providers without a Tiingo token")
    void updateSettingsShouldAcceptProviderBackedMarketDataProviders() {
        AppSettings existingSettings = AppSettings.builder().id("default").build();
        when(appSettingsRepository.findById("default")).thenReturn(Optional.of(existingSettings));
        when(appSettingsRepository.save(any(AppSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SettingsRequest request = SettingsRequest.builder()
                .userAgent("Edgar4j/1.0 (ops@example.com)")
                .autoRefresh(true)
                .refreshInterval(300)
                .darkMode(false)
                .emailNotifications(false)
                .smtpPort(587)
                .smtpStartTlsEnabled(true)
                .marketDataProvider("YAHOOFINANCE")
                .marketDataBaseUrl(null)
                .realtimeSyncEnabled(true)
                .realtimeSyncForms("4")
                .realtimeSyncLookbackHours(1)
                .realtimeSyncMaxPages(10)
                .realtimeSyncPageSize(100)
                .build();

        SettingsResponse response = settingsService.updateSettings(request);

        assertEquals("YAHOOFINANCE", response.getMarketDataProvider());
        assertTrue(response.isMarketDataConfigured());
        assertFalse(response.isMarketDataApiKeyConfigured());
    }

    @Test
    @DisplayName("updateSettings should use provider-config credentials as fallback without persisting them into AppSettings")
    void updateSettingsShouldUseProviderConfigCredentialsAsFallbackWithoutPersistingThem() {
        marketDataProviderProperties.getFinnhub().setEnabled(true);
        marketDataProviderProperties.getFinnhub().setApiKey("property-finnhub-secret");

        AppSettings existingSettings = AppSettings.builder().id("default").build();
        when(appSettingsRepository.findById("default")).thenReturn(Optional.of(existingSettings));
        when(appSettingsRepository.save(any(AppSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SettingsRequest request = SettingsRequest.builder()
                .userAgent("Edgar4j/1.0 (ops@example.com)")
                .autoRefresh(true)
                .refreshInterval(300)
                .darkMode(false)
                .emailNotifications(false)
                .smtpPort(587)
                .smtpStartTlsEnabled(true)
                .marketDataProvider("FINNHUB")
                .marketDataBaseUrl(null)
                .realtimeSyncEnabled(true)
                .realtimeSyncForms("4")
                .realtimeSyncLookbackHours(1)
                .realtimeSyncMaxPages(10)
                .realtimeSyncPageSize(100)
                .build();

        SettingsResponse response = settingsService.updateSettings(request);

        ArgumentCaptor<AppSettings> savedCaptor = ArgumentCaptor.forClass(AppSettings.class);
        verify(appSettingsRepository).save(savedCaptor.capture());

        assertNull(savedCaptor.getValue().getMarketDataApiKey());
        assertNull(savedCaptor.getValue().getMarketDataProviders().getFinnhub().getApiKey());
        assertEquals("FINNHUB", response.getMarketDataProvider());
        assertTrue(response.isMarketDataConfigured());
        assertTrue(response.isMarketDataApiKeyConfigured());
        assertTrue(response.getMarketDataProviders().getFinnhub().isApiKeyConfigured());
        assertTrue(response.getMarketDataProviders().getFinnhub().isConfigured());
    }

    @Test
    @DisplayName("updateSettings should reject non-http market data base URLs")
    void updateSettingsShouldRejectNonHttpMarketDataBaseUrls() {
        AppSettings existingSettings = AppSettings.builder().id("default").build();
        when(appSettingsRepository.findById("default")).thenReturn(Optional.of(existingSettings));

        SettingsRequest request = SettingsRequest.builder()
                .userAgent("Edgar4j/1.0 (ops@example.com)")
                .autoRefresh(true)
                .refreshInterval(300)
                .darkMode(false)
                .emailNotifications(false)
                .smtpPort(587)
                .smtpStartTlsEnabled(true)
                .marketDataProvider("YAHOOFINANCE")
                .marketDataBaseUrl("file:///etc/passwd")
                .realtimeSyncEnabled(true)
                .realtimeSyncForms("4")
                .realtimeSyncLookbackHours(1)
                .realtimeSyncMaxPages(10)
                .realtimeSyncPageSize(100)
                .build();

        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> settingsService.updateSettings(request));

        assertTrue(exception.getMessage().contains("HTTP or HTTPS"));
    }
}
