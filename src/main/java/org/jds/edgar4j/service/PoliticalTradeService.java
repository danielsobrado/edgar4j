package org.jds.edgar4j.service;

import org.jds.edgar4j.dto.request.PoliticalTradeScreenRequest;
import org.jds.edgar4j.dto.request.PoliticalTradeSyncRequest;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.dto.response.PoliticalTradeResponse;
import org.jds.edgar4j.dto.response.PoliticalTradeSyncResponse;

public interface PoliticalTradeService {

    PaginatedResponse<PoliticalTradeResponse> screen(PoliticalTradeScreenRequest request);

    byte[] export(PoliticalTradeScreenRequest request, String format);

    PoliticalTradeSyncResponse sync(PoliticalTradeSyncRequest request);
}
