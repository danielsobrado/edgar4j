package org.jds.edgar4j.service;

import org.jds.edgar4j.dto.request.ExportRequest;

public interface ExportService {

    byte[] exportToCsv(ExportRequest request);

    byte[] exportToJson(ExportRequest request);
}
