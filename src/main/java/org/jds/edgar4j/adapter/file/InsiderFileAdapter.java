package org.jds.edgar4j.adapter.file;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.insider.Insider;
import org.jds.edgar4j.port.InsiderDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class InsiderFileAdapter extends AbstractLongIdFileDataPort<Insider> implements InsiderDataPort {

    private static final String INDEX_CIK = "cik";
    private static final String INDEX_LAST_NAME = "lastName";
    private static final String INDEX_CITY = "city";
    private static final String INDEX_STATE = "state";

    public InsiderFileAdapter(FileStorageEngine storageEngine) {
        super(
                storageEngine.registerCollection(
                        "insiders",
                        Insider.class,
                        FileFormat.JSON,
                        insider -> insider.getId() == null ? null : String.valueOf(insider.getId()),
                        (insider, id) -> insider.setId(Long.parseLong(id))),
                Insider::getId,
                Insider::setId);
        registerExactIndex(INDEX_CIK, Insider::getCik);
        registerIgnoreCaseIndex(INDEX_LAST_NAME, Insider::getLastName);
        registerIgnoreCaseIndex(INDEX_CITY, Insider::getCity);
        registerIgnoreCaseIndex(INDEX_STATE, Insider::getState);
    }

    @Override
    public Optional<Insider> findByCik(String cik) {
        return findFirstByIndex(INDEX_CIK, cik);
    }

    @Override
    public List<Insider> findByFullNameContainingIgnoreCase(String name) {
        return findMatching(insider -> containsIgnoreCase(insider.getFullName(), name));
    }

    @Override
    public List<Insider> findByLastNameIgnoreCase(String lastName) {
        return findAllByIndex(INDEX_LAST_NAME, lastName);
    }

    @Override
    public List<Insider> findByIsActiveTrue() {
        return findMatching(insider -> isTrue(insider.getIsActive()));
    }

    @Override
    public List<Insider> findByInsiderType(Insider.InsiderType insiderType) {
        return findMatching(insider -> insiderType == insider.getInsiderType());
    }

    @Override
    public List<Insider> findByLastTransactionDateAfter(LocalDateTime since) {
        return findMatching(insider -> insider.getLastTransactionDate() != null
                && since != null
                && insider.getLastTransactionDate().isAfter(since));
    }

    @Override
    public List<Insider> findByCityIgnoreCase(String city) {
        return findAllByIndex(INDEX_CITY, city);
    }

    @Override
    public List<Insider> findByStateIgnoreCase(String state) {
        return findAllByIndex(INDEX_STATE, state);
    }

    @Override
    public boolean existsByCik(String cik) {
        return existsByIndex(INDEX_CIK, cik);
    }

    @Override
    public Long countActiveInsiders() {
        return count(insider -> isTrue(insider.getIsActive()));
    }

    @Override
    public List<Insider> findInsidersWithTransactionsForCompany(String cik) {
        return findMatching(insider -> insider.getInsiderTransactions() != null
                && insider.getInsiderTransactions().stream().anyMatch(transaction -> transaction.getCompany() != null
                        && cik != null
                        && cik.equals(transaction.getCompany().getCik())));
    }

    @Override
    public List<Insider> findInsidersWithActiveRelationships() {
        return findMatching(insider -> insider.getCompanyRelationships() != null
                && insider.getCompanyRelationships().stream().anyMatch(relationship -> isTrue(relationship.getIsActive())));
    }
}