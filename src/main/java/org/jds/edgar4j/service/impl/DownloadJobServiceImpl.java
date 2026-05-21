package org.jds.edgar4j.service.impl;

import java.nio.file.Paths;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.jds.edgar4j.dto.request.DownloadRequest;
import org.jds.edgar4j.dto.response.DownloadJobResponse;
import org.jds.edgar4j.dto.response.DownloadSummaryResponse;
import org.jds.edgar4j.dto.response.UsaSpendingCompanyMatchResponse;
import org.jds.edgar4j.dto.response.UsaSpendingCsvPageResponse;
import org.jds.edgar4j.model.DownloadJob;
import org.jds.edgar4j.model.DownloadJob.JobStatus;
import org.jds.edgar4j.model.DownloadJob.JobType;
import org.jds.edgar4j.model.Ticker;
import org.jds.edgar4j.port.DownloadJobDataPort;
import org.jds.edgar4j.port.TickerDataPort;
import org.jds.edgar4j.service.DownloadJobService;
import org.jds.edgar4j.service.UsaSpendingDownloadService;
import org.jds.edgar4j.service.UsaSpendingDownloadService.UsaSpendingCsvPage;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DownloadJobServiceImpl implements DownloadJobService {

    private final DownloadJobDataPort downloadJobRepository;
    private final TickerDataPort tickerRepository;
    private final DownloadJobExecutor downloadJobExecutor;
    private final UsaSpendingDownloadService usaSpendingDownloadService;

    @Override
    public DownloadJobResponse startDownload(DownloadRequest request) {
        log.info("Starting download job: {}", request);

        JobType jobType = mapToJobType(request.getType());

        DownloadJob job = DownloadJob.builder()
                .type(jobType)
                .description(getJobDescription(jobType, request))
                .status(JobStatus.PENDING)
                .progress(0)
                .startedAt(LocalDateTime.now())
                .cik(request.getCik())
                .userAgent(request.getUserAgent())
                .dateFrom(request.getDateFrom())
                .dateTo(request.getDateTo())
                .build();

        job = downloadJobRepository.save(job);

        downloadJobExecutor.executeDownloadAsync(job.getId(), request);

        return toDownloadJobResponse(job);
    }

    @Override
    public Optional<DownloadJobResponse> getJobById(String jobId) {
        return downloadJobRepository.findById(jobId).map(this::toDownloadJobResponse);
    }

    @Override
    public Optional<UsaSpendingCsvPageResponse> getUsaSpendingCsvPage(String jobId, int page, int size) {
        return downloadJobRepository.findById(jobId)
                .filter(job -> job.getType() == JobType.USA_SPENDING_AWARDS)
                .filter(job -> job.getStatus() == JobStatus.COMPLETED)
                .filter(job -> job.getOutputPath() != null && !job.getOutputPath().isBlank())
                .map(job -> toUsaSpendingCsvPageResponse(job, page, size));
    }

    @Override
    public List<DownloadJobResponse> getRecentJobs(int limit) {
        return downloadJobRepository.findTop10ByOrderByStartedAtDesc().stream()
                .limit(limit)
                .map(this::toDownloadJobResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<DownloadJobResponse> getActiveJobs() {
        return downloadJobRepository.findByStatusIn(Arrays.asList(JobStatus.PENDING, JobStatus.IN_PROGRESS))
                .stream()
                .map(this::toDownloadJobResponse)
                .collect(Collectors.toList());
    }

    @Override
    public DownloadSummaryResponse getSummary() {
        List<JobType> tickerJobTypes = Arrays.asList(
                JobType.TICKERS_ALL,
                JobType.TICKERS_NYSE,
                JobType.TICKERS_NASDAQ,
                JobType.TICKERS_MF
        );

        Optional<DownloadJob> latestTickerJob = downloadJobRepository
                .findFirstByTypeInAndStatusOrderByCompletedAtDesc(tickerJobTypes, JobStatus.COMPLETED);

        return DownloadSummaryResponse.builder()
                .tickerRecordsImported(tickerRepository.count())
                .lastTickerUpdate(latestTickerJob.map(DownloadJob::getCompletedAt).orElse(null))
                .build();
    }

    @Override
    public DownloadJob updateJobProgress(String jobId, int progress, long filesDownloaded) {
        DownloadJob job = downloadJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        job.setProgress(progress);
        job.setFilesDownloaded(filesDownloaded);
        return downloadJobRepository.save(job);
    }

    @Override
    public DownloadJob completeJob(String jobId) {
        DownloadJob job = downloadJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        job.setStatus(JobStatus.COMPLETED);
        job.setProgress(100);
        job.setCompletedAt(LocalDateTime.now());
        return downloadJobRepository.save(job);
    }

    @Override
    public DownloadJob failJob(String jobId, String error) {
        DownloadJob job = downloadJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found: " + jobId));

        job.setStatus(JobStatus.FAILED);
        job.setError(error);
        job.setCompletedAt(LocalDateTime.now());
        return downloadJobRepository.save(job);
    }

    @Override
    public boolean cancelJob(String jobId) {
        return downloadJobRepository.findById(jobId)
                .map(this::attemptCancelJob)
                .orElse(false);
    }

    private boolean attemptCancelJob(DownloadJob job) {
        if (job.getStatus() == JobStatus.CANCELLED) {
            return false;
        }

        if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.FAILED) {
            return false;
        }

        job.setStatus(JobStatus.CANCELLED);
        job.setCompletedAt(LocalDateTime.now());
        downloadJobRepository.save(job);
        return true;
    }

    private JobType mapToJobType(DownloadRequest.DownloadType type) {
        switch (type) {
            case TICKERS_ALL:
                return JobType.TICKERS_ALL;
            case TICKERS_NYSE:
                return JobType.TICKERS_NYSE;
            case TICKERS_NASDAQ:
                return JobType.TICKERS_NASDAQ;
            case TICKERS_MF:
                return JobType.TICKERS_MF;
            case SUBMISSIONS:
                return JobType.SUBMISSIONS;
            case REMOTE_FILINGS_SYNC:
                return JobType.REMOTE_FILINGS_SYNC;
            case USA_SPENDING_AWARDS:
                return JobType.USA_SPENDING_AWARDS;
            case BULK_SUBMISSIONS:
                return JobType.BULK_SUBMISSIONS;
            case BULK_COMPANY_FACTS:
                return JobType.BULK_COMPANY_FACTS;
            default:
                throw new IllegalArgumentException("Unknown type: " + type);
        }
    }

    private String getJobDescription(JobType type, DownloadRequest request) {
        switch (type) {
            case TICKERS_ALL:
                return "Download All Company Tickers";
            case TICKERS_NYSE:
                return "Download NYSE Tickers";
            case TICKERS_NASDAQ:
                return "Download NASDAQ Tickers";
            case TICKERS_MF:
                return "Download Mutual Fund Tickers";
            case SUBMISSIONS:
                return "Download Submissions for CIK " + request.getCik();
            case REMOTE_FILINGS_SYNC:
                return String.format(
                        "Sync Remote %s Filings from %s to %s",
                        request.getFormType(),
                        request.getDateFrom(),
                        request.getDateTo()
                );
            case USA_SPENDING_AWARDS:
                return String.format(
                        "Download USAspending Award CSV from %s to %s",
                        request.getDateFrom(),
                        request.getDateTo()
                );
            case BULK_SUBMISSIONS:
                return "Download Bulk Submissions Archive";
            case BULK_COMPANY_FACTS:
                return "Download Company Facts XBRL Archive";
            default:
                return "Unknown Job Type";
        }
    }

    private DownloadJobResponse toDownloadJobResponse(DownloadJob job) {
        return DownloadJobResponse.builder()
                .id(job.getId())
                .type(job.getType().name())
                .description(job.getDescription())
                .status(DownloadJobResponse.JobStatus.valueOf(job.getStatus().name()))
                .progress(job.getProgress())
                .error(job.getError())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .filesDownloaded(job.getFilesDownloaded())
                .totalFiles(job.getTotalFiles())
                .cik(job.getCik())
                .dateFrom(job.getDateFrom())
                .dateTo(job.getDateTo())
                .sourceUrl(job.getSourceUrl())
                .outputPath(job.getOutputPath())
                .build();
    }

    private UsaSpendingCsvPageResponse toUsaSpendingCsvPageResponse(DownloadJob job, int page, int size) {
        UsaSpendingCsvPage csvPage = usaSpendingDownloadService.readCsvPage(
                Paths.get(job.getOutputPath()),
                page,
                size,
                job.getTotalFiles()
        );
        int totalPages = csvPage.totalRows() == 0
                ? 0
                : (int) Math.ceil(csvPage.totalRows() / (double) size);

        return UsaSpendingCsvPageResponse.builder()
                .jobId(job.getId())
                .fileName(csvPage.fileName())
                .headers(csvPage.headers())
                .rows(csvPage.rows())
                .rowMatches(matchRows(csvPage))
                .page(page)
                .size(size)
                .totalRows(csvPage.totalRows())
                .totalPages(totalPages)
                .build();
    }

    private List<List<UsaSpendingCompanyMatchResponse>> matchRows(UsaSpendingCsvPage csvPage) {
        Map<String, Integer> headerIndexes = indexHeaders(csvPage.headers());
        List<EdgarCandidate> candidates = loadEdgarCandidates();

        return csvPage.rows().stream()
                .map(row -> matchRow(row, headerIndexes, candidates))
                .collect(Collectors.toList());
    }

    private Map<String, Integer> indexHeaders(List<String> headers) {
        Map<String, Integer> indexes = new LinkedHashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            indexes.put(headers.get(i), i);
        }
        return indexes;
    }

    private List<EdgarCandidate> loadEdgarCandidates() {
        List<EdgarCandidate> candidates = new ArrayList<>();
        for (Ticker company : tickerRepository.findAll()) {
            String normalizedName = normalizeCompanyName(company.getName());
            if (!normalizedName.isBlank()) {
                candidates.add(new EdgarCandidate(
                        normalizeCik(company.getCik()),
                        company.getCode(),
                        company.getName(),
                        normalizedName,
                        tokens(normalizedName)
                ));
            }
        }
        return candidates;
    }

    private List<UsaSpendingCompanyMatchResponse> matchRow(
            List<String> row,
            Map<String, Integer> headerIndexes,
            List<EdgarCandidate> candidates) {
        List<SourceName> sourceNames = sourceNames(row, headerIndexes);
        if (sourceNames.isEmpty() || candidates.isEmpty()) {
            return List.of();
        }

        Map<String, UsaSpendingCompanyMatchResponse> bestByCompany = new LinkedHashMap<>();
        for (SourceName sourceName : sourceNames) {
            String normalizedSource = normalizeCompanyName(sourceName.value());
            if (normalizedSource.isBlank()) {
                continue;
            }
            Set<String> sourceTokens = tokens(normalizedSource);

            for (EdgarCandidate candidate : candidates) {
                MatchScore score = scoreMatch(normalizedSource, sourceTokens, candidate);
                if (score.confidence() < 70) {
                    continue;
                }

                UsaSpendingCompanyMatchResponse match = UsaSpendingCompanyMatchResponse.builder()
                        .cik(candidate.cik())
                        .ticker(candidate.ticker())
                        .companyName(candidate.companyName())
                        .confidence(score.confidence())
                        .sourceField(sourceName.field())
                        .sourceValue(sourceName.value())
                        .matchMethod(score.method())
                        .build();

                String key = candidate.cik() != null ? candidate.cik() : candidate.ticker();
                UsaSpendingCompanyMatchResponse existing = bestByCompany.get(key);
                if (existing == null || match.getConfidence() > existing.getConfidence()) {
                    bestByCompany.put(key, match);
                }
            }
        }

        return bestByCompany.values().stream()
                .sorted(Comparator
                        .comparingInt(UsaSpendingCompanyMatchResponse::getConfidence)
                        .reversed()
                        .thenComparing(UsaSpendingCompanyMatchResponse::getCompanyName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .limit(3)
                .collect(Collectors.toList());
    }

    private List<SourceName> sourceNames(List<String> row, Map<String, Integer> headerIndexes) {
        List<SourceName> names = new ArrayList<>();
        for (String field : List.of(
                "recipient_name",
                "recipient_name_raw",
                "recipient_parent_name",
                "recipient_parent_name_raw")) {
            Integer index = headerIndexes.get(field);
            if (index == null || index >= row.size()) {
                continue;
            }
            String value = row.get(index);
            if (value != null && !value.isBlank()) {
                names.add(new SourceName(field, value.trim()));
            }
        }
        return names;
    }

    private MatchScore scoreMatch(String normalizedSource, Set<String> sourceTokens, EdgarCandidate candidate) {
        if (normalizedSource.equals(candidate.normalizedName())) {
            return new MatchScore(100, "normalized_exact");
        }

        Set<String> intersection = new HashSet<>(sourceTokens);
        intersection.retainAll(candidate.tokens());
        if (intersection.isEmpty()) {
            return new MatchScore(0, "token_overlap");
        }

        if (sourceTokens.containsAll(candidate.tokens()) || candidate.tokens().containsAll(sourceTokens)) {
            int shorter = Math.min(sourceTokens.size(), candidate.tokens().size());
            int longer = Math.max(sourceTokens.size(), candidate.tokens().size());
            int confidence = Math.max(82, (int) Math.round((shorter * 96.0) / longer));
            return new MatchScore(confidence, "token_contains");
        }

        Set<String> union = new HashSet<>(sourceTokens);
        union.addAll(candidate.tokens());
        int confidence = (int) Math.round((intersection.size() * 90.0) / union.size());
        return new MatchScore(confidence, "token_overlap");
    }

    private String normalizeCompanyName(String value) {
        if (value == null) {
            return "";
        }

        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toUpperCase(Locale.ROOT)
                .replace("&", " AND ")
                .replaceAll("[^A-Z0-9 ]", " ")
                .replaceAll("\\b(THE|A|AN)\\b", " ")
                .replaceAll("\\b(INCORPORATED|INC|CORPORATION|CORP|COMPANY|CO|LIMITED|LTD|LLC|L L C|PLC|LP|L P|LLP|L L P|HOLDINGS|HOLDING|GROUP)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private Set<String> tokens(String normalizedName) {
        if (normalizedName == null || normalizedName.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(normalizedName.split(" "))
                .filter(token -> token.length() > 1)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private String normalizeCik(String cik) {
        if (cik == null || cik.isBlank()) {
            return null;
        }
        String digits = cik.trim().replaceFirst("^0+(?!$)", "");
        if (!digits.matches("\\d+")) {
            return cik.trim();
        }
        return String.format("%010d", Long.parseLong(digits));
    }

    private record SourceName(String field, String value) {
    }

    private record EdgarCandidate(String cik, String ticker, String companyName, String normalizedName, Set<String> tokens) {
    }

    private record MatchScore(int confidence, String method) {
    }
}

