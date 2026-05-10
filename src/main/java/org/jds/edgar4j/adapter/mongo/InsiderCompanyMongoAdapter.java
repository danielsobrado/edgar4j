package org.jds.edgar4j.adapter.mongo;

import java.util.LinkedHashSet;
import java.util.List;

import org.jds.edgar4j.model.insider.Company;
import org.jds.edgar4j.port.InsiderCompanyDataPort;
import org.jds.edgar4j.repository.insider.CompanyRepository;
import org.jds.edgar4j.repository.insider.InsiderTransactionRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class InsiderCompanyMongoAdapter implements InsiderCompanyDataPort {

    @Delegate(excludes = CustomCompanyQueries.class, types = InsiderCompanyDataPort.class)
    private final CompanyRepository repository;

    private final InsiderTransactionRepository transactionRepository;

    @Override
    public List<Company> findCompaniesWithTransactionsSince(java.time.LocalDateTime since) {
        if (since == null) {
            return List.of();
        }

        return transactionRepository.findByTransactionDateAfterOrderByTransactionDateDesc(since.toLocalDate()).stream()
                .map(transaction -> transaction.getCompany())
                .filter(company -> company != null && company.getId() != null)
                .map(company -> repository.findById(company.getId()).orElse(company))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf));
    }

    private interface CustomCompanyQueries {
        List<Company> findCompaniesWithTransactionsSince(java.time.LocalDateTime since);
    }
}