package org.jds.edgar4j.repository.insider;

import java.time.LocalDate;
import java.util.List;

import org.jds.edgar4j.model.insider.InsiderCompanyRelationship;
import org.springframework.context.annotation.Profile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

/**
 * Repository interface for InsiderCompanyRelationship entities
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Profile("resource-high")
public interface InsiderCompanyRelationshipRepository extends MongoRepository<InsiderCompanyRelationship, Long> {

    /**
     * Find relationships by insider CIK
     */
    List<InsiderCompanyRelationship> findByInsiderCik(String insiderCik);

    /**
     * Find relationships by company CIK
     */
    List<InsiderCompanyRelationship> findByCompanyCik(String companyCik);

    /**
     * Find active relationships
     */
    List<InsiderCompanyRelationship> findByIsActiveTrue();

    /**
     * Find relationships by insider and company
     */
    List<InsiderCompanyRelationship> findByInsiderCikAndCompanyCik(String insiderCik, String companyCik);

    /**
     * Find active relationships by insider and company
     */
    List<InsiderCompanyRelationship> findByInsiderCikAndCompanyCikAndIsActiveTrue(String insiderCik, String companyCik);

    /**
     * Find director relationships
     */
    List<InsiderCompanyRelationship> findByIsDirectorTrue();

    /**
     * Find officer relationships
     */
    List<InsiderCompanyRelationship> findByIsOfficerTrue();

    /**
     * Find 10% owner relationships
     */
    List<InsiderCompanyRelationship> findByIsTenPercentOwnerTrue();

    /**
     * Find directors for company
     */
    List<InsiderCompanyRelationship> findByCompanyCikAndIsDirectorTrueAndIsActiveTrue(String companyCik);

    /**
     * Find officers for company
     */
    List<InsiderCompanyRelationship> findByCompanyCikAndIsOfficerTrueAndIsActiveTrue(String companyCik);

    /**
     * Find 10% owners for company
     */
    List<InsiderCompanyRelationship> findByCompanyCikAndIsTenPercentOwnerTrueAndIsActiveTrue(String companyCik);

    /**
     * Find relationships by officer title
     */
    List<InsiderCompanyRelationship> findByOfficerTitleContainingIgnoreCase(String title);

    /**
     * Find relationships that started after date
     */
    List<InsiderCompanyRelationship> findByStartDateAfter(LocalDate date);

    /**
     * Find relationships that ended before date
     */
    List<InsiderCompanyRelationship> findByEndDateBefore(LocalDate date);

    /**
     * Find current relationships (active and within date range)
     */
    @Query("{ 'isActive': true, 'startDate': { $lte: ?0 }, $or: [ { 'endDate': null }, { 'endDate': { $gt: ?0 } } ] }")
    List<InsiderCompanyRelationship> findCurrentRelationships(LocalDate currentDate);

    /**
     * Find current relationships for company
     */
    @Query("{ 'company.cik': ?0, 'isActive': true, 'startDate': { $lte: ?1 }, $or: [ { 'endDate': null }, { 'endDate': { $gt: ?1 } } ] }")
    List<InsiderCompanyRelationship> findCurrentRelationshipsForCompany(String cik, LocalDate currentDate);

    /**
     * Find current relationships for insider
     */
    @Query("{ 'insider.cik': ?0, 'isActive': true, 'startDate': { $lte: ?1 }, $or: [ { 'endDate': null }, { 'endDate': { $gt: ?1 } } ] }")
    List<InsiderCompanyRelationship> findCurrentRelationshipsForInsider(String cik, LocalDate currentDate);

    /**
     * Count relationships by company
     */
    @Query(value = "{ 'company.cik': ?0, 'isActive': true }", count = true)
    Long countActiveRelationshipsByCompany(String cik);

    /**
     * Count relationships by insider
     */
    @Query(value = "{ 'insider.cik': ?0, 'isActive': true }", count = true)
    Long countActiveRelationshipsByInsider(String cik);

    /**
     * Find relationships with insider status
     */
    @Query("{ 'isActive': true, $or: [ { 'isDirector': true }, { 'isOfficer': true }, { 'isTenPercentOwner': true } ] }")
    List<InsiderCompanyRelationship> findRelationshipsWithInsiderStatus();
}

