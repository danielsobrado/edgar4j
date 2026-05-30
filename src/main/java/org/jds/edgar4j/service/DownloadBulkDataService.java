package org.jds.edgar4j.service;

import java.nio.file.Path;

public interface DownloadBulkDataService {

    BulkDownloadResult downloadBulkSubmissionsArchive();

    BulkDownloadResult downloadBulkCompanyFactsArchive();

    record BulkDownloadResult(long filesDownloaded, String sourceUrl, Path outputPath) {
    }
}
