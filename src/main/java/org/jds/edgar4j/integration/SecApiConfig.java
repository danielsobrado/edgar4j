package org.jds.edgar4j.integration;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import lombok.Getter;

@Getter
@Configuration
public class SecApiConfig {

    private static final DateTimeFormatter DAILY_INDEX_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    @Value("${edgar4j.urls.baseSecUrl}")
    private String baseSecUrl;

    @Value("${edgar4j.urls.baseDataSecUrl}")
    private String baseDataSecUrl;

    @Value("${edgar4j.urls.edgarDataArchivesUrl}")
    private String edgarDataArchivesUrl;

    @Value("${edgar4j.urls.submissionsUrl}")
    private String submissionsUrl;

    @Value("${edgar4j.urls.submissionsCIKUrl}")
    private String submissionsCIKUrl;

    @Value("${edgar4j.urls.bulkSubmissionsFileUrl}")
    private String bulkSubmissionsFileUrl;

    @Value("${edgar4j.urls.bulkCompanyFactsFileUrl}")
    private String bulkCompanyFactsFileUrl;

    @Value("${edgar4j.urls.companyTickersUrl}")
    private String companyTickersUrl;

    @Value("${edgar4j.urls.companyTickersExchangesUrl}")
    private String companyTickersExchangesUrl;

    @Value("${edgar4j.urls.companyTickersMFsUrl}")
    private String companyTickersMFsUrl;

    public String formatCik(String cik) {
        try {
            long cikLong = Long.parseLong(cik);
            return String.format("%010d", cikLong);
        } catch (NumberFormatException e) {
            return cik;
        }
    }

    public String getSubmissionUrl(String cik) {
        return submissionsCIKUrl + formatCik(cik) + ".json";
    }

    public String getForm4Url(String cik, String accessionNumber, String primaryDocument) {
        String cleanedAccession = accessionNumber.replace("-", "");
        return String.format("%s/%s/%s/%s", edgarDataArchivesUrl, cik, cleanedAccession, primaryDocument);
    }

    /**
     * Gets the URL for any EDGAR filing document.
     */
    public String getFilingUrl(String cik, String accessionNumber, String document) {
        String cleanedAccession = accessionNumber.replace("-", "");
        return String.format("%s/%s/%s/%s", edgarDataArchivesUrl, formatCik(cik), cleanedAccession, document);
    }

    public List<String> getDailyMasterIndexUrls(LocalDate date) {
        String formattedDate = date.format(DAILY_INDEX_DATE_FORMAT);
        int quarter = ((date.getMonthValue() - 1) / 3) + 1;

        return List.of(
                String.format("%s/Archives/edgar/daily-index/master.%s.idx", baseSecUrl, formattedDate),
                String.format("%s/Archives/edgar/daily-index/%d/QTR%d/master.%s.idx",
                        baseSecUrl, date.getYear(), quarter, formattedDate)
        );
    }

    public String getArchiveUrl(String archivePath) {
        String normalizedPath = archivePath.startsWith("/") ? archivePath.substring(1) : archivePath;
        return String.format("%s/Archives/%s", baseSecUrl, normalizedPath);
    }
}
