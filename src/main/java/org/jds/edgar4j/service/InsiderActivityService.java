package org.jds.edgar4j.service;

import org.jds.edgar4j.dto.request.InsiderActivityScreenRequest;
import org.jds.edgar4j.dto.response.InsiderActivityCoverageResponse;
import org.jds.edgar4j.dto.response.InsiderActivityResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;

import java.time.LocalDate;

public interface InsiderActivityService {

    PaginatedResponse<InsiderActivityResponse> screen(InsiderActivityScreenRequest request);

    byte[] export(InsiderActivityScreenRequest request, String format);

    InsiderActivityCoverageResponse coverage(String form, LocalDate from, LocalDate to);
}
