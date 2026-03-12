package org.jds.edgar4j.properties;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "edgar4j.cors")
public class CorsProperties {
    /**
     * Comma-separated list supported via env var binding.
     */
    private List<String> allowedOrigins = List.of(
            "http://localhost:3000",
            "http://localhost:5173",
            "http://localhost:5174",
            "http://127.0.0.1:3000",
            "http://127.0.0.1:5173",
            "http://127.0.0.1:5174"
    );

    private boolean allowCredentials = true;

    private long maxAgeSeconds = 3600;
}

