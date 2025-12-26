package org.jds.edgar4j.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsRequest {

    private String userAgent;
    private boolean autoRefresh;
    private int refreshInterval;
    private boolean darkMode;
    private boolean emailNotifications;
}
