package org.jds.edgar4j.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jds.edgar4j.config.AppConstants;
import org.jds.edgar4j.dto.request.FilingSearchRequest;
import org.jds.edgar4j.dto.response.FilingDetailResponse;
import org.jds.edgar4j.dto.response.FilingResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.port.FilingSearchPort;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.service.FilingService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FilingServiceImpl implements FilingService {

    private final FillingDataPort fillingRepository;
    private final FilingSearchPort filingSearchPort;

    @Override
    public PaginatedResponse<FilingResponse> searchFilings(FilingSearchRequest request) {
        log.info("Searching filings with request: {}", request);

        String sortDirection = request.getSortDir();
        if (sortDirection == null || sortDirection.isBlank()) {
            sortDirection = AppConstants.DEFAULT_SORT_DIRECTION;
        }

        String sortField = request.getSortBy();
        if (sortField == null || sortField.isBlank()) {
            sortField = "fillingDate";
        }

        Sort sort = Sort.by(
                sortDirection.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC,
                sortField
        );
        PageRequest pageRequest = PageRequest.of(request.getPage(), request.getSize(), sort);

        Page<FilingSearchPort.SearchResult> page = filingSearchPort.search(toSearchCriteria(request), pageRequest);

        List<String> filingIds = page.getContent().stream()
                .map(FilingSearchPort.SearchResult::id)
                .toList();
        Map<String, Filling> filingsById = fillingRepository.findAllById(filingIds).stream()
                .collect(Collectors.toMap(Filling::getId, filling -> filling));

        var content = filingIds.stream()
                .map(filingsById::get)
                .filter(filing -> filing != null)
                .map(this::toFilingResponse)
                .collect(Collectors.toList());

        return PaginatedResponse.of(content, request.getPage(), request.getSize(), page.getTotalElements());
    }

    @Override
    public Optional<FilingDetailResponse> getFilingById(String id) {
        return fillingRepository.findById(id).map(this::toFilingDetailResponse);
    }

    @Override
    public Optional<FilingDetailResponse> getFilingByAccessionNumber(String accessionNumber) {
        return fillingRepository.findByAccessionNumber(accessionNumber).map(this::toFilingDetailResponse);
    }

    @Override
    public PaginatedResponse<FilingResponse> getFilingsByCompany(String companyId, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "fillingDate"));
        Page<Filling> filingsPage = fillingRepository.findByCompany(companyId, pageRequest);

        var content = filingsPage.getContent().stream()
                .map(this::toFilingResponse)
                .collect(Collectors.toList());

        return PaginatedResponse.of(content, page, size, filingsPage.getTotalElements());
    }

    @Override
    public PaginatedResponse<FilingResponse> getFilingsByCik(String cik, int page, int size) {
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "fillingDate"));
        Page<Filling> filingsPage = fillingRepository.findByCik(cik, pageRequest);

        var content = filingsPage.getContent().stream()
                .map(this::toFilingResponse)
                .collect(Collectors.toList());

        return PaginatedResponse.of(content, page, size, filingsPage.getTotalElements());
    }

    @Override
    public List<FilingResponse> getRecentFilings(int limit) {
        return fillingRepository.findTop10ByOrderByFillingDateDesc().stream()
                .limit(limit)
                .map(this::toFilingResponse)
                .collect(Collectors.toList());
    }

    @Override
    public Filling saveFilling(Filling filling) {
        log.info("Saving filing with accession number: {}", filling.getAccessionNumber());
        return fillingRepository.save(filling);
    }

    @Override
    public List<Filling> saveAllFillings(List<Filling> fillings) {
        log.info("Saving {} filings", fillings.size());
        return fillingRepository.saveAll(fillings);
    }

    @Override
    public long countFilings() {
        return fillingRepository.count();
    }

    @Override
    public long countFilingsByFormType(String formType) {
        return fillingRepository.countByFormTypeNumber(formType);
    }

    private FilingSearchPort.SearchCriteria toSearchCriteria(FilingSearchRequest request) {
        String query = null;
        if (request.getKeywords() != null && !request.getKeywords().isEmpty()) {
            query = request.getKeywords().stream()
                    .filter(keyword -> keyword != null && !keyword.isBlank())
                    .collect(Collectors.joining(" "));
        }
        if ((query == null || query.isBlank()) && request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
            query = request.getCompanyName();
        }

        return new FilingSearchPort.SearchCriteria(
                query,
                request.getFormTypes(),
                request.getCik(),
                request.getTicker(),
            request.getDateFrom() == null ? null : java.time.Instant.ofEpochMilli(request.getDateFrom().getTime())
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate(),
            request.getDateTo() == null ? null : java.time.Instant.ofEpochMilli(request.getDateTo().getTime())
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate());
    }

    private FilingResponse toFilingResponse(Filling f) {
        return FilingResponse.builder()
                .id(f.getId())
                .companyName(f.getCompany())
                .cik(f.getCik())
                .formType(f.getFormType() != null ? f.getFormType().getNumber() : null)
                .formTypeDescription(f.getFormType() != null ? f.getFormType().getDescription() : null)
                .filingDate(f.getFillingDate())
                .reportDate(f.getReportDate())
                .accessionNumber(f.getAccessionNumber())
                .primaryDocument(f.getPrimaryDocument())
                .primaryDocDescription(f.getPrimaryDocDescription())
                .url(f.getUrl())
                .isXBRL(f.isXBRL())
                .isInlineXBRL(f.isInlineXBRL())
                .build();
    }

    private FilingDetailResponse toFilingDetailResponse(Filling f) {
        return FilingDetailResponse.builder()
                .id(f.getId())
                .companyName(f.getCompany())
                .cik(f.getCik())
                .formType(f.getFormType() != null ? f.getFormType().getNumber() : null)
                .formTypeDescription(f.getFormType() != null ? f.getFormType().getDescription() : null)
                .filingDate(f.getFillingDate())
                .reportDate(f.getReportDate())
                .accessionNumber(f.getAccessionNumber())
                .fileNumber(f.getFileNumber())
                .filmNumber(f.getFilmNumber())
                .primaryDocument(f.getPrimaryDocument())
                .primaryDocDescription(f.getPrimaryDocDescription())
                .url(f.getUrl())
                .items(f.getItems())
                .isXBRL(f.isXBRL())
                .isInlineXBRL(f.isInlineXBRL())
                .build();
    }
}
