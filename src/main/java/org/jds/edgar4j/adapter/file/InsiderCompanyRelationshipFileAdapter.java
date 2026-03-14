package org.jds.edgar4j.adapter.file;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.model.insider.InsiderCompanyRelationship;
import org.jds.edgar4j.port.InsiderCompanyRelationshipDataPort;
import org.jds.edgar4j.storage.file.FileFormat;
import org.jds.edgar4j.storage.file.FileStorageEngine;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-low")
public class InsiderCompanyRelationshipFileAdapter extends AbstractLongIdFileDataPort<InsiderCompanyRelationship>
        implements InsiderCompanyRelationshipDataPort {

    private static final String INDEX_INSIDER_CIK = "insiderCik";
    private static final String INDEX_COMPANY_CIK = "companyCik";
    private static final String INDEX_OFFICER_TITLE = "officerTitle";

    public InsiderCompanyRelationshipFileAdapter(FileStorageEngine storageEngine) {
        super(
                storageEngine.registerCollection(
                        "insider_company_relationships",
                        InsiderCompanyRelationship.class,
                        FileFormat.JSON,
                        relationship -> relationship.getId() == null ? null : String.valueOf(relationship.getId()),
                        (relationship, id) -> relationship.setId(Long.parseLong(id))),
                InsiderCompanyRelationship::getId,
                InsiderCompanyRelationship::setId);
        registerExactIndex(INDEX_INSIDER_CIK, relationship -> insiderCik(relationship));
        registerExactIndex(INDEX_COMPANY_CIK, relationship -> companyCik(relationship));
        registerIgnoreCaseIndex(INDEX_OFFICER_TITLE, InsiderCompanyRelationship::getOfficerTitle);
    }

    @Override
    public List<InsiderCompanyRelationship> findByInsiderCik(String insiderCik) {
        return findAllByIndex(INDEX_INSIDER_CIK, insiderCik);
    }

    @Override
    public List<InsiderCompanyRelationship> findByCompanyCik(String companyCik) {
        return findAllByIndex(INDEX_COMPANY_CIK, companyCik);
    }

    @Override
    public List<InsiderCompanyRelationship> findByIsActiveTrue() {
        return findMatching(relationship -> isTrue(relationship.getIsActive()));
    }

    @Override
    public List<InsiderCompanyRelationship> findByInsiderCikAndCompanyCik(String insiderCik, String companyCik) {
        return findMatching(relationship -> insiderCik.equals(insiderCik(relationship))
                && companyCik.equals(companyCik(relationship)));
    }

    @Override
    public List<InsiderCompanyRelationship> findByInsiderCikAndCompanyCikAndIsActiveTrue(String insiderCik, String companyCik) {
        return findMatching(relationship -> insiderCik.equals(insiderCik(relationship))
                && companyCik.equals(companyCik(relationship))
                && isTrue(relationship.getIsActive()));
    }

    @Override
    public List<InsiderCompanyRelationship> findByIsDirectorTrue() {
        return findMatching(relationship -> isTrue(relationship.getIsDirector()));
    }

    @Override
    public List<InsiderCompanyRelationship> findByIsOfficerTrue() {
        return findMatching(relationship -> isTrue(relationship.getIsOfficer()));
    }

    @Override
    public List<InsiderCompanyRelationship> findByIsTenPercentOwnerTrue() {
        return findMatching(relationship -> isTrue(relationship.getIsTenPercentOwner()));
    }

    @Override
    public List<InsiderCompanyRelationship> findByCompanyCikAndIsDirectorTrueAndIsActiveTrue(String companyCik) {
        return findMatching(relationship -> companyCik.equals(companyCik(relationship))
                && isTrue(relationship.getIsDirector())
                && isTrue(relationship.getIsActive()));
    }

    @Override
    public List<InsiderCompanyRelationship> findByCompanyCikAndIsOfficerTrueAndIsActiveTrue(String companyCik) {
        return findMatching(relationship -> companyCik.equals(companyCik(relationship))
                && isTrue(relationship.getIsOfficer())
                && isTrue(relationship.getIsActive()));
    }

    @Override
    public List<InsiderCompanyRelationship> findByCompanyCikAndIsTenPercentOwnerTrueAndIsActiveTrue(String companyCik) {
        return findMatching(relationship -> companyCik.equals(companyCik(relationship))
                && isTrue(relationship.getIsTenPercentOwner())
                && isTrue(relationship.getIsActive()));
    }

    @Override
    public List<InsiderCompanyRelationship> findByOfficerTitleContainingIgnoreCase(String title) {
        return findMatching(relationship -> containsIgnoreCase(relationship.getOfficerTitle(), title));
    }

    @Override
    public List<InsiderCompanyRelationship> findByStartDateAfter(LocalDate date) {
        return findMatching(relationship -> relationship.getStartDate() != null
                && date != null
                && relationship.getStartDate().isAfter(date));
    }

    @Override
    public List<InsiderCompanyRelationship> findByEndDateBefore(LocalDate date) {
        return findMatching(relationship -> relationship.getEndDate() != null
                && date != null
                && relationship.getEndDate().isBefore(date));
    }

    @Override
    public List<InsiderCompanyRelationship> findCurrentRelationships(LocalDate currentDate) {
        return findMatching(relationship -> isCurrentRelationship(relationship, currentDate));
    }

    @Override
    public List<InsiderCompanyRelationship> findCurrentRelationshipsForCompany(String cik, LocalDate currentDate) {
        return findMatching(relationship -> cik.equals(companyCik(relationship))
                && isCurrentRelationship(relationship, currentDate));
    }

    @Override
    public List<InsiderCompanyRelationship> findCurrentRelationshipsForInsider(String cik, LocalDate currentDate) {
        return findMatching(relationship -> cik.equals(insiderCik(relationship))
                && isCurrentRelationship(relationship, currentDate));
    }

    @Override
    public Long countActiveRelationshipsByCompany(String cik) {
        return count(relationship -> cik.equals(companyCik(relationship)) && isTrue(relationship.getIsActive()));
    }

    @Override
    public Long countActiveRelationshipsByInsider(String cik) {
        return count(relationship -> cik.equals(insiderCik(relationship)) && isTrue(relationship.getIsActive()));
    }

    @Override
    public List<InsiderCompanyRelationship> findRelationshipsWithInsiderStatus() {
        return findMatching(relationship -> isTrue(relationship.getIsActive())
                && (isTrue(relationship.getIsDirector())
                        || isTrue(relationship.getIsOfficer())
                        || isTrue(relationship.getIsTenPercentOwner())));
    }

    private boolean isCurrentRelationship(InsiderCompanyRelationship relationship, LocalDate currentDate) {
        return isTrue(relationship.getIsActive())
                && relationship.getStartDate() != null
                && currentDate != null
                && !relationship.getStartDate().isAfter(currentDate)
                && (relationship.getEndDate() == null || relationship.getEndDate().isAfter(currentDate));
    }

    private String insiderCik(InsiderCompanyRelationship relationship) {
        return relationship.getInsider() != null ? relationship.getInsider().getCik() : null;
    }

    private String companyCik(InsiderCompanyRelationship relationship) {
        return relationship.getCompany() != null ? relationship.getCompany().getCik() : null;
    }
}