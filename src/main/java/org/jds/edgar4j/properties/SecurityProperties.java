package org.jds.edgar4j.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "edgar4j.security")
public class SecurityProperties {
    /**
     * When enabled, protects API and actuator endpoints using HTTP Basic auth.
     */
    private boolean enabled = false;

    /**
     * Username for HTTP Basic auth when security is enabled.
     */
    private String username;

    /**
     * Password for HTTP Basic auth when security is enabled.
     */
    private String password;
}

