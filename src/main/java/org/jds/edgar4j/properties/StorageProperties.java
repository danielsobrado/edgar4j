package org.jds.edgar4j.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "edgar4j.storage")
public class StorageProperties {
    /**
     * Local directory where downloaded daily master index files are stored.
     */
    private String dailyIndexesPath = "./data/daily-indexes";
}

