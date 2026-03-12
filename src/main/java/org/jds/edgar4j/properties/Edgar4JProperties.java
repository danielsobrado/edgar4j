package org.jds.edgar4j.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * Configuration properties for Edgar4J application
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "edgar4j")
public class Edgar4JProperties {
    
    private Urls urls = new Urls();
    private Persistence persistence = new Persistence();
    private String userAgent = "Edgar4J/1.0";
    private String dailyIndexesPath = "/tmp/edgar4j/daily-indexes";

    @Data
    public static class Urls {
        private String baseSecUrl = "https://www.sec.gov";
        private String baseDataSecUrl = "https://data.sec.gov";
        private String edgarDataArchivesUrl = "https://www.sec.gov/Archives/edgar/data";
        private String submissionsUrl = "https://data.sec.gov/submissions";
        private String submissionsCIKUrl = "https://data.sec.gov/submissions/CIK";
        private String bulkSubmissionsFileUrl = "https://www.sec.gov/Archives/edgar/daily-index/bulkdata/submissions.zip";
        private String bulkCompanyFactsFileUrl = "https://www.sec.gov/Archives/edgar/daily-index/xbrl/companyfacts.zip";
        private String companyTickersUrl = "https://www.sec.gov/files/company_tickers.json";
        private String companyTickersExchangesUrl = "https://www.sec.gov/files/company_tickers_exchange.json";
        private String companyTickersMFsUrl = "https://www.sec.gov/files/company_tickers_mf.json";
    }

    @Data
    public static class Persistence {
        private String database = "postgresql";
    }

    public String getDailyMasterUrl(LocalDate date) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd");
        String formattedDate = date.format(formatter);
        return String.format("%s/%s/master.idx", dailyIndexesPath, formattedDate);
    }
}
