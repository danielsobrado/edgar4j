package org.jds.edgar4j.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import org.jds.edgar4j.dto.response.DashboardStatsResponse;
import org.jds.edgar4j.dto.response.FilingResponse;
import org.jds.edgar4j.dto.response.RecentSearchResponse;
import org.jds.edgar4j.entity.SearchHistory;
import org.jds.edgar4j.repository.FillingRepository;
import org.jds.edgar4j.repository.SearchHistoryRepository;
import org.jds.edgar4j.repository.SubmissionsRepository;
import org.jds.edgar4j.service.DashboardService;
import org.jds.edgar4j.service.FilingService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private final FillingRepository fillingRepository;
    private final SubmissionsRepository submissionsRepository;
    private final SearchHistoryRepository searchHistoryRepository;
    private final FilingService filingService;

    @Override
    public DashboardStatsResponse getStats() {
        log.info("Getting dashboard stats");

        long totalFilings = fillingRepository.count();
        long companiesTracked = submissionsRepository.count();
        long form4Count = fillingRepository.countByFormTypeNumber("4");
        long form10KCount = fillingRepository.countByFormTypeNumber("10-K");
        long form10QCount = fillingRepository.countByFormTypeNumber("10-Q");

        return DashboardStatsResponse.builder()
                .totalFilings(totalFilings)
                .companiesTracked(companiesTracked)
                .lastSync(LocalDateTime.now())
                .filingsTodayCount(0)
                .form4Count(form4Count)
                .form10KCount(form10KCount)
                .form10QCount(form10QCount)
                .build();
    }

    @Override
    public List<RecentSearchResponse> getRecentSearches(int limit) {
        return searchHistoryRepository.findTop10ByOrderByTimestampDesc().stream()
                .limit(limit)
                .map(this::toRecentSearchResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<FilingResponse> getRecentFilings(int limit) {
        return filingService.getRecentFilings(limit);
    }

    @Override
    public void recordSearch(String query, String type, int resultCount) {
        log.info("Recording search: query={}, type={}, resultCount={}", query, type, resultCount);

        SearchHistory searchHistory = SearchHistory.builder()
                .query(query)
                .type(type)
                .timestamp(LocalDateTime.now())
                .resultCount(resultCount)
                .build();

        searchHistoryRepository.save(searchHistory);
    }

    private RecentSearchResponse toRecentSearchResponse(SearchHistory sh) {
        return RecentSearchResponse.builder()
                .id(sh.getId())
                .query(sh.getQuery())
                .type(sh.getType())
                .timestamp(sh.getTimestamp())
                .resultCount(sh.getResultCount())
                .build();
    }
}
