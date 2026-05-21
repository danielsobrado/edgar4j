package org.jds.edgar4j.service;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

public interface UsaSpendingDownloadService {

    UsaSpendingDownloadResult downloadAwardCsvZip(LocalDate dateFrom, LocalDate dateTo);

    UsaSpendingCsvPage readCsvPage(Path archivePath, int page, int size, long totalRowsHint);

    record UsaSpendingDownloadResult(Path outputPath, String sourceUrl, long totalRows) {
    }

    record UsaSpendingCsvPage(String fileName, List<String> headers, List<List<String>> rows, long totalRows) {
    }
}
