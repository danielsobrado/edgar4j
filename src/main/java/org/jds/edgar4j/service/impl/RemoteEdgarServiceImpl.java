package org.jds.edgar4j.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.jds.edgar4j.dto.response.RemoteSubmissionFilingResponse;
import org.jds.edgar4j.dto.response.RemoteSubmissionResponse;
import org.jds.edgar4j.dto.response.RemoteTickerResponse;
import org.jds.edgar4j.integration.SecApiClient;
import org.jds.edgar4j.integration.SecResponseParser;
import org.jds.edgar4j.integration.model.SecSubmissionResponse;
import org.jds.edgar4j.model.Ticker;
import org.jds.edgar4j.service.RemoteEdgarService;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class RemoteEdgarServiceImpl implements RemoteEdgarService {

    private static final int MAX_TICKER_LIMIT = 500;
    private static final int MAX_FILINGS_LIMIT = 200;

    private final SecApiClient secApiClient;
    private final SecResponseParser responseParser;

    @Override
    public List<RemoteTickerResponse> getRemoteTickers(String source, String search, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_TICKER_LIMIT));
        String safeSource = source == null ? "all" : source.toLowerCase(Locale.ROOT);
        String normalizedSearch = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);

        List<Ticker> parsedTickers = switch (safeSource) {
            case "exchanges" -> responseParser.parseTickersExchangeJson(secApiClient.fetchCompanyTickersExchanges());
            case "mf" -> responseParser.parseTickersJson(secApiClient.fetchCompanyTickersMutualFunds());
            default -> responseParser.parseTickersJson(secApiClient.fetchCompanyTickers());
        };

        return parsedTickers.stream()
                .filter(ticker -> matchesSearch(ticker, normalizedSearch))
                .sorted(Comparator.comparing(ticker -> safeString(ticker.getCode())))
                .limit(safeLimit)
                .map(ticker -> RemoteTickerResponse.builder()
                        .cik(ticker.getCik())
                        .ticker(ticker.getCode())
                        .name(ticker.getName())
                        .exchange(ticker.getExchange() != null ? ticker.getExchange().getCode() : null)
                        .build())
                .collect(Collectors.toList());
    }

    @Override
    public RemoteSubmissionResponse getRemoteSubmission(String cik, int filingsLimit) {
        int safeFilingsLimit = Math.max(1, Math.min(filingsLimit, MAX_FILINGS_LIMIT));
        String json = secApiClient.fetchSubmissions(cik);
        SecSubmissionResponse submission = responseParser.parseSubmissionResponse(json);

        List<RemoteSubmissionFilingResponse> recentFilings = mapRecentFilings(submission, safeFilingsLimit);

        return RemoteSubmissionResponse.builder()
                .cik(submission.getCik())
                .companyName(submission.getName())
                .sic(submission.getSic())
                .sicDescription(submission.getSicDescription())
                .tickers(submission.getTickers())
                .exchanges(submission.getExchanges())
                .recentFilingsCount(recentFilings.size())
                .recentFilings(recentFilings)
                .build();
    }

    private List<RemoteSubmissionFilingResponse> mapRecentFilings(SecSubmissionResponse submission, int limit) {
        List<RemoteSubmissionFilingResponse> filings = new ArrayList<>();
        if (submission.getFilings() == null || submission.getFilings().getRecent() == null) {
            return filings;
        }

        SecSubmissionResponse.Recent recent = submission.getFilings().getRecent();
        if (recent.getAccessionNumber() == null || recent.getAccessionNumber().isEmpty()) {
            return filings;
        }

        int max = Math.min(limit, recent.getAccessionNumber().size());
        for (int i = 0; i < max; i++) {
            filings.add(RemoteSubmissionFilingResponse.builder()
                    .accessionNumber(getOrNull(recent.getAccessionNumber(), i))
                    .formType(getOrNull(recent.getForm(), i))
                    .filingDate(getOrNull(recent.getFilingDate(), i))
                    .reportDate(getOrNull(recent.getReportDate(), i))
                    .primaryDocument(getOrNull(recent.getPrimaryDocument(), i))
                    .primaryDocDescription(getOrNull(recent.getPrimaryDocDescription(), i))
                    .build());
        }
        return filings;
    }

    private boolean matchesSearch(Ticker ticker, String search) {
        if (search.isEmpty()) {
            return true;
        }
        String code = safeString(ticker.getCode()).toLowerCase(Locale.ROOT);
        String name = safeString(ticker.getName()).toLowerCase(Locale.ROOT);
        String cik = safeString(ticker.getCik()).toLowerCase(Locale.ROOT);
        return code.contains(search) || name.contains(search) || cik.contains(search);
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String getOrNull(List<String> list, int index) {
        if (list == null || index >= list.size()) {
            return null;
        }
        return list.get(index);
    }
}

