package org.jds.edgar4j.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsResponse {

    private String userAgent;
    private boolean autoRefresh;
    private int refreshInterval;
    private boolean darkMode;
    private boolean emailNotifications;

    private ApiEndpointsInfo apiEndpoints;
    private ConnectionStatus mongoDbStatus;
    private ConnectionStatus elasticsearchStatus;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ApiEndpointsInfo {
        private String baseSecUrl;
        private String submissionsUrl;
        private String edgarArchivesUrl;
        private String companyTickersUrl;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConnectionStatus {
        private boolean connected;
        private String message;
        private long latencyMs;
    }
}
