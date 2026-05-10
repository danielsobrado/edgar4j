package org.jds.edgar4j.service;

public interface DownloadBulkDataService {

    long downloadBulkSubmissionsArchive();

    long downloadBulkCompanyFactsArchive();
}
