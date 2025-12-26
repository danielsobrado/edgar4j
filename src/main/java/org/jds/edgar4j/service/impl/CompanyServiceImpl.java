package org.jds.edgar4j.service.impl;

import java.util.Optional;
import java.util.stream.Collectors;

import org.jds.edgar4j.dto.request.CompanySearchRequest;
import org.jds.edgar4j.dto.response.CompanyListResponse;
import org.jds.edgar4j.dto.response.CompanyResponse;
import org.jds.edgar4j.dto.response.PaginatedResponse;
import org.jds.edgar4j.entity.Submissions;
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

    private final SubmissionsRepository submissionsRepository;

    @Override
    public PaginatedResponse<CompanyListResponse> searchCompanies(CompanySearchRequest request) {
        log.info("Searching companies with request: {}", request);

        Sort sort = Sort.by(
                request.getSortDir().equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC,
                request.getSortBy()
        );
        PageRequest pageRequest = PageRequest.of(request.getPage(), request.getSize(), sort);

        Page<Submissions> page;
        if (request.getSearchTerm() != null && !request.getSearchTerm().isEmpty()) {
            page = submissionsRepository.searchByCompanyNameOrCik(request.getSearchTerm(), pageRequest);
        } else {
            page = submissionsRepository.findAll(pageRequest);
        }

        var content = page.getContent().stream()
                .map(this::toCompanyListResponse)
                .collect(Collectors.toList());

        return PaginatedResponse.of(content, request.getPage(), request.getSize(), page.getTotalElements());
    }

    @Override
    public Optional<CompanyResponse> getCompanyById(String id) {
        return submissionsRepository.findById(id).map(this::toCompanyResponse);
    }

    @Override
    public Optional<CompanyResponse> getCompanyByCik(String cik) {
        return submissionsRepository.findByCik(cik).map(this::toCompanyResponse);
    }

    @Override
    public Optional<CompanyResponse> getCompanyByTicker(String ticker) {
        return submissionsRepository.findByCompanyNameContainingIgnoreCase(ticker)
                .stream()
                .findFirst()
                .map(this::toCompanyResponse);
    }

    @Override
    public Submissions saveSubmissions(Submissions submissions) {
        log.info("Saving submissions for CIK: {}", submissions.getCik());
        return submissionsRepository.save(submissions);
    }

    @Override
    public long countCompanies() {
        return submissionsRepository.count();
    }

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
}
