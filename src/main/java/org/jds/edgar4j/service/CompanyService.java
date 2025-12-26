package org.jds.edgar4j.service;

import java.util.Optional;

import org.jds.edgar4j.dto.request.CompanySearchRequest;
import org.jds.edgar4j.dto.response.CompanyListResponse;
import org.jds.edgar4j.dto.response.CompanyResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.entity.Submissions;

public interface CompanyService {

    PaginatedResponse<CompanyListResponse> searchCompanies(CompanySearchRequest request);

    Optional<CompanyResponse> getCompanyById(String id);

    Optional<CompanyResponse> getCompanyByCik(String cik);

    Optional<CompanyResponse> getCompanyByTicker(String ticker);

    Submissions saveSubmissions(Submissions submissions);

    long countCompanies();
}
