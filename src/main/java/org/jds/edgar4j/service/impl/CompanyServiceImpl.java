package org.jds.edgar4j.service.impl;

import java.util.Optional;
import java.util.stream.Collectors;

import org.jds.edgar4j.dto.request.CompanySearchRequest;
import org.jds.edgar4j.dto.response.CompanyListResponse;
import org.jds.edgar4j.dto.response.CompanyResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.model.CompanyTicker;
import org.jds.edgar4j.model.Submissions;
import org.jds.edgar4j.repository.CompanyTickerRepository;
import org.jds.edgar4j.repository.SubmissionsRepository;
import org.jds.edgar4j.service.CompanyService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final SubmissionsRepository    submissionsRepository;
    private final CompanyTickerRepository  companyTickerRepository;

    // ─── Company search ───────────────────────────────────────────────────────

    @Override
    public PaginatedResponse<CompanyListResponse> searchCompanies(CompanySearchRequest request) {
        log.info("Searching companies with request: {}", request);

        String sortDir = request.getSortDir() != null ? request.getSortDir() : "desc";
        Sort sort = Sort.by(
                sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC,
                request.getSortBy()
        );
        PageRequest pageRequest = PageRequest.of(request.getPage(), request.getSize(), sort);

        Page<Submissions> page = (request.getSearchTerm() != null && !request.getSearchTerm().isEmpty())
                ? submissionsRepository.searchByCompanyNameOrCik(request.getSearchTerm(), pageRequest)
                : submissionsRepository.findAll(pageRequest);

        var content = page.getContent().stream()
                .map(this::toCompanyListResponse)
                .collect(Collectors.toList());

        return PaginatedResponse.of(content, request.getPage(), request.getSize(), page.getTotalElements());
    }

    // ─── ID-based lookup ──────────────────────────────────────────────────────

    @Override
    public Optional<CompanyResponse> getCompanyById(String id) {
        return submissionsRepository.findById(id).map(this::toCompanyResponse);
    }

    // ─── CIK lookup (company_tickers first) ──────────────────────────────────

    /**
     * Looks up a company by CIK.
     *
     * <ol>
     *   <li>Parse the CIK to a Long and query {@code company_tickers} to get the
     *       canonical ticker + company name quickly.</li>
     *   <li>Then load the full {@code submissions} document for the padded CIK.</li>
     *   <li>If no {@code submissions} document exists, fall back to the minimal info
     *       from {@code company_tickers}.</li>
     * </ol>
     */
    @Override
    public Optional<CompanyResponse> getCompanyByCik(String cik) {
        log.debug("getCompanyByCik: {}", cik);

        Long cikNum = parseCikToLong(cik);

        // Step 1 – find ticker info from company_tickers
        Optional<CompanyTicker> tickerEntry = (cikNum != null)
                ? companyTickerRepository.findFirstByCikStr(cikNum)
                : Optional.empty();

        // Step 2 – load full submissions document (try both padded and raw forms)
        Optional<Submissions> submissions = submissionsRepository.findByCik(cik);
        if (submissions.isEmpty() && cikNum != null) {
            // try zero-padded form that submissions collection uses
            submissions = submissionsRepository.findByCik(String.format("%010d", cikNum));
        }

        if (submissions.isPresent()) {
            CompanyResponse response = toCompanyResponse(submissions.get());
            // Enrich ticker from company_tickers if submissions doesn't have one
            if ((response.getTicker() == null || response.getTicker().isBlank())
                    && tickerEntry.isPresent()) {
                response.setTicker(tickerEntry.get().getTicker());
            }
            return Optional.of(response);
        }

        // Fallback: minimal response from company_tickers only
        return tickerEntry.map(ct -> CompanyResponse.builder()
                .cik(ct.getCikPadded())
                .ticker(ct.getTicker())
                .name(ct.getTitle())
                .build());
    }

    // ─── Ticker lookup (company_tickers first) ────────────────────────────────

    /**
     * Looks up a company by ticker symbol.
     *
     * <ol>
     *   <li>Query {@code company_tickers} for the canonical CIK of this ticker.</li>
     *   <li>Load the full {@code submissions} document using that CIK.</li>
     *   <li>Fall back to minimal company_tickers info if no submissions exist.</li>
     *   <li>Last resort: legacy search directly in submissions.tickers array.</li>
     * </ol>
     */
    @Override
    public Optional<CompanyResponse> getCompanyByTicker(String ticker) {
        log.debug("getCompanyByTicker: {}", ticker);

        String upperTicker = ticker.toUpperCase();

        Optional<CompanyTicker> tickerEntry = companyTickerRepository.findByTickerIgnoreCase(upperTicker);

        if (tickerEntry.isPresent()) {
            CompanyTicker ct = tickerEntry.get();
            String paddedCik = ct.getCikPadded();

            Optional<Submissions> submissions = (paddedCik != null)
                    ? submissionsRepository.findByCik(paddedCik)
                    : Optional.empty();

            if (submissions.isEmpty() && ct.getCikStr() != null) {
                // also try the raw numeric string
                submissions = submissionsRepository.findByCik(String.valueOf(ct.getCikStr()));
            }

            if (submissions.isPresent()) {
                return Optional.of(toCompanyResponse(submissions.get()));
            }

            // Fallback: build minimal response from company_tickers
            return Optional.of(CompanyResponse.builder()
                    .cik(paddedCik)
                    .ticker(ct.getTicker())
                    .name(ct.getTitle())
                    .build());
        }

        // Legacy fallback: search submissions.tickers array directly
        return submissionsRepository.findByTickersContaining(upperTicker)
                .stream()
                .findFirst()
                .map(this::toCompanyResponse);
    }

    // ─── Lightweight CIK ↔ Ticker lookups ────────────────────────────────────

    @Override
    public Optional<String> getCikByTicker(String ticker) {
        return companyTickerRepository.findByTickerIgnoreCase(ticker.toUpperCase())
                .map(CompanyTicker::getCikPadded);
    }

    @Override
    public Optional<String> getTickerByCik(String cik) {
        Long cikNum = parseCikToLong(cik);
        if (cikNum == null) return Optional.empty();
        return companyTickerRepository.findFirstByCikStr(cikNum)
                .map(CompanyTicker::getTicker);
    }

    @Override
    public Optional<CompanyTicker> getCompanyTickerByTicker(String ticker) {
        return companyTickerRepository.findByTickerIgnoreCase(ticker.toUpperCase());
    }

    @Override
    public Optional<CompanyTicker> getCompanyTickerByCik(String cik) {
        Long cikNum = parseCikToLong(cik);
        if (cikNum == null) return Optional.empty();
        return companyTickerRepository.findFirstByCikStr(cikNum);
    }

    // ─── Admin / persistence ─────────────────────────────────────────────────

    @Override
    public Submissions saveSubmissions(Submissions submissions) {
        log.info("Saving submissions for CIK: {}", submissions.getCik());
        return submissionsRepository.save(submissions);
    }

    @Override
    public long countCompanies() {
        return submissionsRepository.count();
    }

    // ─── Mappers ──────────────────────────────────────────────────────────────

    private CompanyListResponse toCompanyListResponse(Submissions s) {
        return CompanyListResponse.builder()
                .id(s.getId())
                .name(s.getCompanyName())
                .ticker(s.getTickers() != null && !s.getTickers().isEmpty() ? s.getTickers().get(0) : null)
                .cik(s.getCik())
                .sic(s.getSic())
                .sicDescription(s.getSicDescription())
                .stateOfIncorporation(s.getStateOfIncorporation())
                .filingCount(s.getFillingCount())
                .build();
    }

    private CompanyResponse toCompanyResponse(Submissions s) {
        return CompanyResponse.builder()
                .id(s.getId())
                .name(s.getCompanyName())
                .ticker(s.getTickers() != null && !s.getTickers().isEmpty() ? s.getTickers().get(0) : null)
                .cik(s.getCik())
                .sic(s.getSic())
                .sicDescription(s.getSicDescription())
                .entityType(s.getEntityType())
                .stateOfIncorporation(s.getStateOfIncorporation())
                .stateOfIncorporationDescription(s.getStateOfIncorporationDescription())
                .fiscalYearEnd(s.getFiscalYearEnd())
                .ein(s.getEin())
                .description(s.getDescription())
                .website(s.getWebsite())
                .investorWebsite(s.getInvestorWebsite())
                .category(s.getCategory())
                .tickers(s.getTickers())
                .exchanges(s.getExchanges())
                .filingCount(s.getFillingCount())
                .hasInsiderTransactions(s.isInsiderTransactionForOwnerExists() || s.isInsiderTransactionForIssuerExists())
                .build();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    /**
     * Parses a CIK string to Long, stripping leading zeros.
     * Accepts "320193", "0000320193", etc.
     */
    private Long parseCikToLong(String cik) {
        if (cik == null || cik.isBlank()) return null;
        try {
            return Long.parseLong(cik.trim().replaceFirst("^0+(?!$)", ""));
        } catch (NumberFormatException e) {
            log.warn("Could not parse CIK '{}' as a number", cik);
            return null;
        }
    }
}
