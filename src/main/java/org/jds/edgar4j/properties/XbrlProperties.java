package org.jds.edgar4j.properties;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "edgar4j.xbrl")
public class XbrlProperties {
    /**
     * Allow-list of hostnames (or parent domains) that URL-based XBRL endpoints may access.
     * Subdomains of an entry are also allowed (e.g., "data.sec.gov" is allowed by "sec.gov").
     */
    private List<String> allowedHosts = List.of("sec.gov");

    /**
     * When true, allows http://localhost and http://127.0.0.1 for local development.
     */
    private boolean allowHttpLocalhost = true;
}

