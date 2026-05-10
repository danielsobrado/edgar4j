package org.jds.edgar4j.adapter.mongo;

import java.util.LinkedHashSet;
import java.util.List;

import org.jds.edgar4j.model.insider.Insider;
import org.jds.edgar4j.port.InsiderDataPort;
import org.jds.edgar4j.repository.insider.InsiderCompanyRelationshipRepository;
import org.jds.edgar4j.repository.insider.InsiderRepository;
import org.jds.edgar4j.repository.insider.InsiderTransactionRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class InsiderMongoAdapter implements InsiderDataPort {

    @Delegate(excludes = CustomInsiderQueries.class, types = InsiderDataPort.class)
    private final InsiderRepository repository;

    private final InsiderTransactionRepository transactionRepository;
    private final InsiderCompanyRelationshipRepository relationshipRepository;

    @Override
    public List<Insider> findInsidersWithTransactionsForCompany(String cik) {
        return transactionRepository.findByCompanyCik(cik).stream()
                .map(transaction -> transaction.getInsider())
                .filter(insider -> insider != null && insider.getId() != null)
                .map(insider -> repository.findById(insider.getId()).orElse(insider))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf));
    }

    @Override
    public List<Insider> findInsidersWithActiveRelationships() {
        return relationshipRepository.findByIsActiveTrue().stream()
                .map(relationship -> relationship.getInsider())
                .filter(insider -> insider != null && insider.getId() != null)
                .map(insider -> repository.findById(insider.getId()).orElse(insider))
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toCollection(LinkedHashSet::new),
                        List::copyOf));
    }

    private interface CustomInsiderQueries {
        List<Insider> findInsidersWithTransactionsForCompany(String cik);

        List<Insider> findInsidersWithActiveRelationships();
    }
}
