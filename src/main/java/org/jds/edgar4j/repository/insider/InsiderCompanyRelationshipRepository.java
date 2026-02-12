package org.jds.edgar4j.repository.insider;

import org.jds.edgar4j.model.insider.InsiderCompanyRelationship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Repository interface for InsiderCompanyRelationship entities
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Repository
public interface InsiderCompanyRelationshipRepository extends JpaRepository<InsiderCompanyRelationship, Long> {

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
    @Query("SELECT r FROM InsiderCompanyRelationship r WHERE r.isActive = true AND r.startDate <= :currentDate AND (r.endDate IS NULL OR r.endDate > :currentDate)")
    List<InsiderCompanyRelationship> findCurrentRelationships(@Param("currentDate") LocalDate currentDate);

    /**
     * Find current relationships for company
     */
    @Query("SELECT r FROM InsiderCompanyRelationship r WHERE r.company.cik = :cik AND r.isActive = true AND r.startDate <= :currentDate AND (r.endDate IS NULL OR r.endDate > :currentDate)")
    List<InsiderCompanyRelationship> findCurrentRelationshipsForCompany(@Param("cik") String cik, @Param("currentDate") LocalDate currentDate);

    /**
     * Find current relationships for insider
     */
    @Query("SELECT r FROM InsiderCompanyRelationship r WHERE r.insider.cik = :cik AND r.isActive = true AND r.startDate <= :currentDate AND (r.endDate IS NULL OR r.endDate > :currentDate)")
    List<InsiderCompanyRelationship> findCurrentRelationshipsForInsider(@Param("cik") String cik, @Param("currentDate") LocalDate currentDate);

    /**
     * Count relationships by company
     */
    @Query("SELECT COUNT(r) FROM InsiderCompanyRelationship r WHERE r.company.cik = :cik AND r.isActive = true")
    Long countActiveRelationshipsByCompany(@Param("cik") String cik);

    /**
     * Count relationships by insider
     */
    @Query("SELECT COUNT(r) FROM InsiderCompanyRelationship r WHERE r.insider.cik = :cik AND r.isActive = true")
    Long countActiveRelationshipsByInsider(@Param("cik") String cik);

    /**
     * Find relationships with insider status
     */
    @Query("SELECT r FROM InsiderCompanyRelationship r WHERE (r.isDirector = true OR r.isOfficer = true OR r.isTenPercentOwner = true) AND r.isActive = true")
    List<InsiderCompanyRelationship> findRelationshipsWithInsiderStatus();
}
