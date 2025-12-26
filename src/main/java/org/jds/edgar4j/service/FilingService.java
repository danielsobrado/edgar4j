package org.jds.edgar4j.service;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.dto.request.FilingSearchRequest;
import org.jds.edgar4j.dto.response.FilingDetailResponse;
import org.jds.edgar4j.dto.response.FilingResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.entity.Filling;

public interface FilingService {

    PaginatedResponse<FilingResponse> searchFilings(FilingSearchRequest request);

    Optional<FilingDetailResponse> getFilingById(String id);

    Optional<FilingDetailResponse> getFilingByAccessionNumber(String accessionNumber);

    PaginatedResponse<FilingResponse> getFilingsByCompany(String companyId, int page, int size);

    PaginatedResponse<FilingResponse> getFilingsByCik(String cik, int page, int size);

    List<FilingResponse> getRecentFilings(int limit);

    Filling saveFilling(Filling filling);

    List<Filling> saveAllFillings(List<Filling> fillings);

    long countFilings();

    long countFilingsByFormType(String formType);
}
