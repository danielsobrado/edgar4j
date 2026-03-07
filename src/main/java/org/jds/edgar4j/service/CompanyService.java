package org.jds.edgar4j.service;

import java.util.Optional;

import org.jds.edgar4j.dto.request.CompanySearchRequest;
import org.jds.edgar4j.dto.response.CompanyListResponse;
import org.jds.edgar4j.dto.response.CompanyResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.model.CompanyTicker;
import org.jds.edgar4j.model.Submissions;

public interface CompanyService {

    PaginatedResponse<CompanyListResponse> searchCompanies(CompanySearchRequest request);

    Optional<CompanyResponse> getCompanyById(String id);

    /** Look up company by CIK, using company_tickers as primary source. */
    Optional<CompanyResponse> getCompanyByCik(String cik);

    /** Look up company by ticker symbol, using company_tickers as primary source. */
    Optional<CompanyResponse> getCompanyByTicker(String ticker);

    /**
     * Returns the zero-padded CIK string for a given ticker (e.g. "AAPL" → "0000320193").
     * Queries the {@code company_tickers} collection only — no full Submissions load.
     */
    Optional<String> getCikByTicker(String ticker);

    /**
     * Returns the ticker symbol for a given CIK string (e.g. "0000320193" or "320193").
     * Queries the {@code company_tickers} collection only — no full Submissions load.
     */
    Optional<String> getTickerByCik(String cik);

    /** Returns the raw CompanyTicker document for a given ticker. */
    Optional<CompanyTicker> getCompanyTickerByTicker(String ticker);

    /** Returns the raw CompanyTicker document for a given CIK. */
    Optional<CompanyTicker> getCompanyTickerByCik(String cik);

    Submissions saveSubmissions(Submissions submissions);

    long countCompanies();
}
