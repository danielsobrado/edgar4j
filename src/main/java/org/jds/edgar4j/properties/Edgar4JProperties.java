package org.jds.edgar4j.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Data
@Configuration
@ConfigurationProperties(prefix = "edgar")
public class Edgar4JProperties {
    private String userAgent;
    private String dailyIndexesPath;

    public String getDailyMasterUrl(LocalDate date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        String formattedDate = date.format(formatter);
        return String.format("%s/%s/master.idx", dailyIndexesPath, formattedDate);
    }

}
