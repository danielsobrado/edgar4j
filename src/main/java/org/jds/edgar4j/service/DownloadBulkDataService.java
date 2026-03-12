package org.jds.edgar4j.service;

import java.nio.file.Path;

public interface DownloadBulkDataService {

    Path downloadBulkSubmissionsArchive();

    Path downloadBulkCompanyFactsArchive();
}
