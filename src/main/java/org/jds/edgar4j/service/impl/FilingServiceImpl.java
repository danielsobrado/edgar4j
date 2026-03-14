package org.jds.edgar4j.service.impl;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jds.edgar4j.config.AppConstants;
import org.jds.edgar4j.dto.request.FilingSearchRequest;
import org.jds.edgar4j.dto.response.FilingDetailResponse;
import org.jds.edgar4j.dto.response.FilingResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.model.Filling;
import org.jds.edgar4j.port.CompanyTickerDataPort;
import org.jds.edgar4j.port.FillingDataPort;
import org.jds.edgar4j.port.TickerDataPort;
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
    private final CompanyTickerDataPort companyTickerRepository;
    private final TickerDataPort tickerRepository;

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

        Page<Filling> page;

        if (request.getCik() != null && !request.getCik().isEmpty()) {
            if (request.getFormTypes() != null && !request.getFormTypes().isEmpty()) {
                page = request.getFormTypes().size() == 1
                        ? fillingRepository.findByCikAndFormType(request.getCik(), request.getFormTypes().get(0), pageRequest)
                        : fillingRepository.findByCikAndFormTypeIn(request.getCik(), request.getFormTypes(), pageRequest);
            } else {
                page = fillingRepository.findByCik(request.getCik(), pageRequest);
            }
        } else if (request.getTicker() != null && !request.getTicker().isEmpty()) {
            page = searchByTicker(request, pageRequest);
        } else if (request.getDateFrom() != null && request.getDateTo() != null && request.getFormTypes() != null) {
            page = fillingRepository.searchFillings(request.getDateFrom(), request.getDateTo(), request.getFormTypes(), pageRequest);
        } else if (request.getCompanyName() != null && !request.getCompanyName().isEmpty()) {
            page = fillingRepository.searchByCompanyOrCik(request.getCompanyName(), pageRequest);
        } else {
            page = fillingRepository.findAllByOrderByFillingDateDesc(pageRequest);
        }

        var content = page.getContent().stream()
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

    private Page<Filling> searchByTicker(FilingSearchRequest request, PageRequest pageRequest) {
        Optional<List<String>> tickerCiks = tickerRepository.findByCode(request.getTicker().trim().toUpperCase())
                .map(ticker -> List.of(ticker.getCik()));

        Optional<List<String>> companyTickerCiks = companyTickerRepository.findByTickerIgnoreCase(request.getTicker())
                .map(companyTicker -> {
                    String rawCik = String.valueOf(companyTicker.getCikStr());
                    String paddedCik = String.format("%010d", companyTicker.getCikStr());
                    return rawCik.equals(paddedCik) ? List.of(rawCik) : List.of(rawCik, paddedCik);
                });

        return tickerCiks.or(() -> companyTickerCiks)
                .map(ciks -> {
                    if (request.getFormTypes() != null && !request.getFormTypes().isEmpty()) {
                        return request.getFormTypes().size() == 1
                                ? fillingRepository.findByCikInAndFormType(ciks, request.getFormTypes().get(0), pageRequest)
                                : fillingRepository.findByCikInAndFormTypeIn(ciks, request.getFormTypes(), pageRequest);
                    }

                    return fillingRepository.findByCikIn(ciks, pageRequest);
                })
                .orElseGet(() -> fillingRepository.searchByCompanyOrCik(request.getTicker(), pageRequest));
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
