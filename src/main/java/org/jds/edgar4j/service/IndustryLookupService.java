package org.jds.edgar4j.service;

import java.util.Optional;

import org.jds.edgar4j.model.Company;

/**
 * Service for looking up company industry classification
 * Fetches company data from SEC EDGAR submissions API
 *
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-11-05
 */
public interface IndustryLookupService {

    /**
     * Get company information including industry classification by CIK
     *
     * @param cik Company CIK (Central Index Key)
     * @return Optional containing Company with industry info, empty if not found
     */
    Optional<Company> getCompanyByCik(String cik);

    /**
     * Get industry description by CIK
     *
     * @param cik Company CIK
     * @return Industry description (e.g., "State Commercial Banks") or null if not found
     */
    String getIndustryByCik(String cik);

    /**
     * Get SIC code by CIK
     *
     * @param cik Company CIK
     * @return SIC code or null if not found
     */
    String getSicCodeByCik(String cik);

    /**
     * Clear the cache (for testing or refresh)
     */
    void clearCache();
}
