package org.jds.edgar4j.service;

import org.jds.edgar4j.dto.request.PoliticalTradeScreenRequest;
import org.jds.edgar4j.dto.request.PoliticalTradeSyncRequest;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.dto.response.PoliticalTradeCoverageResponse;
import org.jds.edgar4j.dto.response.PoliticalTradeResponse;
import org.jds.edgar4j.dto.response.PoliticalTradeSyncResponse;
import java.time.LocalDate;
import java.util.List;

public interface PoliticalTradeService {

    PaginatedResponse<PoliticalTradeResponse> screen(PoliticalTradeScreenRequest request);

    byte[] export(PoliticalTradeScreenRequest request, String format);

    List<String> politicians(String query, int limit);

    PoliticalTradeCoverageResponse coverage(LocalDate from, LocalDate to);

    PoliticalTradeSyncResponse sync(PoliticalTradeSyncRequest request);
}
