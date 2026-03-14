package org.jds.edgar4j.port;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.model.insider.InsiderCompanyRelationship;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface InsiderCompanyRelationshipDataPort extends BaseInsiderDataPort<InsiderCompanyRelationship> {

    List<InsiderCompanyRelationship> findByInsiderCik(String insiderCik);

    List<InsiderCompanyRelationship> findByCompanyCik(String companyCik);

    List<InsiderCompanyRelationship> findByIsActiveTrue();

    List<InsiderCompanyRelationship> findByInsiderCikAndCompanyCik(String insiderCik, String companyCik);

    List<InsiderCompanyRelationship> findByInsiderCikAndCompanyCikAndIsActiveTrue(String insiderCik, String companyCik);

    List<InsiderCompanyRelationship> findByIsDirectorTrue();

    List<InsiderCompanyRelationship> findByIsOfficerTrue();

    List<InsiderCompanyRelationship> findByIsTenPercentOwnerTrue();

    List<InsiderCompanyRelationship> findByCompanyCikAndIsDirectorTrueAndIsActiveTrue(String companyCik);

    List<InsiderCompanyRelationship> findByCompanyCikAndIsOfficerTrueAndIsActiveTrue(String companyCik);

    List<InsiderCompanyRelationship> findByCompanyCikAndIsTenPercentOwnerTrueAndIsActiveTrue(String companyCik);

    List<InsiderCompanyRelationship> findByOfficerTitleContainingIgnoreCase(String title);

    List<InsiderCompanyRelationship> findByStartDateAfter(LocalDate date);

    List<InsiderCompanyRelationship> findByEndDateBefore(LocalDate date);

    List<InsiderCompanyRelationship> findCurrentRelationships(LocalDate currentDate);

    List<InsiderCompanyRelationship> findCurrentRelationshipsForCompany(String cik, LocalDate currentDate);

    List<InsiderCompanyRelationship> findCurrentRelationshipsForInsider(String cik, LocalDate currentDate);

    Long countActiveRelationshipsByCompany(String cik);

    Long countActiveRelationshipsByInsider(String cik);

    List<InsiderCompanyRelationship> findRelationshipsWithInsiderStatus();
}
