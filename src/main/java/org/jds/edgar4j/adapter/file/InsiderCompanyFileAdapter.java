package org.jds.edgar4j.adapter.file;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.insider.Company;
import org.jds.edgar4j.port.InsiderCompanyDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class InsiderCompanyFileAdapter extends AbstractLongIdFileDataPort<Company> implements InsiderCompanyDataPort {

    private static final String INDEX_CIK = "cik";
    private static final String INDEX_TICKER = "tickerSymbol";
    private static final String INDEX_EXCHANGE = "exchange";
    private static final String INDEX_SECTOR = "sector";
    private static final String INDEX_SIC_CODE = "sicCode";

    public InsiderCompanyFileAdapter(FileStorageEngine storageEngine) {
        super(
                storageEngine.registerCollection(
                        "insider_companies",
                        Company.class,
                        FileFormat.JSON,
                        company -> company.getId() == null ? null : String.valueOf(company.getId()),
                        (company, id) -> company.setId(Long.parseLong(id))),
                Company::getId,
                Company::setId);
        registerExactIndex(INDEX_CIK, Company::getCik);
        registerIgnoreCaseIndex(INDEX_TICKER, Company::getTickerSymbol);
        registerIgnoreCaseIndex(INDEX_EXCHANGE, Company::getExchange);
        registerIgnoreCaseIndex(INDEX_SECTOR, Company::getSector);
        registerExactIndex(INDEX_SIC_CODE, Company::getSicCode);
    }

    @Override
    public Optional<Company> findByCik(String cik) {
        return findFirstByIndex(INDEX_CIK, cik);
    }

    @Override
    public Optional<Company> findByTickerSymbol(String tickerSymbol) {
        return findFirstByIndex(INDEX_TICKER, tickerSymbol);
    }

    @Override
    public List<Company> findByCompanyNameContainingIgnoreCase(String name) {
        return findMatching(company -> containsIgnoreCase(company.getCompanyName(), name));
    }

    @Override
    public List<Company> findByIsActiveTrue() {
        return findMatching(company -> isTrue(company.getIsActive()));
    }

    @Override
    public List<Company> findByExchange(String exchange) {
        return findAllByIndex(INDEX_EXCHANGE, exchange);
    }

    @Override
    public List<Company> findBySector(String sector) {
        return findAllByIndex(INDEX_SECTOR, sector);
    }

    @Override
    public List<Company> findByLastFilingDateAfter(LocalDateTime since) {
        return findMatching(company -> company.getLastFilingDate() != null
                && since != null
                && company.getLastFilingDate().isAfter(since));
    }

    @Override
    public List<Company> findBySicCode(String sicCode) {
        return findAllByIndex(INDEX_SIC_CODE, sicCode);
    }

    @Override
    public boolean existsByCik(String cik) {
        return existsByIndex(INDEX_CIK, cik);
    }

    @Override
    public boolean existsByTickerSymbol(String tickerSymbol) {
        return existsByIndex(INDEX_TICKER, tickerSymbol);
    }

    @Override
    public Long countActiveCompanies() {
        return count(company -> isTrue(company.getIsActive()));
    }

    @Override
    public List<Company> findCompaniesWithTransactionsSince(LocalDateTime since) {
        return findMatching(company -> company.getInsiderTransactions() != null
                && company.getInsiderTransactions().stream().anyMatch(transaction -> transaction.getTransactionDate() != null
                        && since != null
                        && transaction.getTransactionDate().atStartOfDay().isAfter(since)));
    }
}