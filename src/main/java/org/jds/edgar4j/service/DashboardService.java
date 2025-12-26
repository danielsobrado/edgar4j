package org.jds.edgar4j.service;

import java.util.List;

import org.jds.edgar4j.dto.response.DashboardStatsResponse;
import org.jds.edgar4j.dto.response.FilingResponse;
import org.jds.edgar4j.dto.response.RecentSearchResponse;

public interface DashboardService {

    DashboardStatsResponse getStats();

    List<RecentSearchResponse> getRecentSearches(int limit);

    List<FilingResponse> getRecentFilings(int limit);

    void recordSearch(String query, String type, int resultCount);
}
