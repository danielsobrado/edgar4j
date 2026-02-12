package org.jds.edgar4j.service.insider.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jds.edgar4j.model.insider.Insider;
import org.jds.edgar4j.repository.insider.InsiderRepository;
import org.jds.edgar4j.repository.insider.InsiderCompanyRelationshipRepository;
import org.jds.edgar4j.repository.insider.InsiderTransactionRepository;
import org.jds.edgar4j.service.insider.InsiderService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Implementation of InsiderService for managing insider data
 * 
 * @author J. Daniel Sobrado
 * @version 1.0
 * @since 2025-01-01
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InsiderServiceImpl implements InsiderService {

    private final InsiderRepository insiderRepository;
    private final InsiderTransactionRepository transactionRepository;
    private final InsiderCompanyRelationshipRepository relationshipRepository;

    @Override
    public Insider saveInsider(Insider insider) {
        log.debug("Saving insider: {}", insider.getFullName());
        
        // Ensure CIK is properly formatted
        if (insider.getCik() != null) {
            insider.setCik(insider.getCik());
        }
        
        // Construct full name if not provided
        if (insider.getFullName() == null || insider.getFullName().isEmpty()) {
            insider.constructFullName();
        }
        
        return insiderRepository.save(insider);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Insider> findByCik(String cik) {
        log.debug("Finding insider by CIK: {}", cik);
        String formattedCik = formatCik(cik);
        return insiderRepository.findByCik(formattedCik);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Insider> findByName(String name) {
        log.debug("Finding insiders by name: {}", name);
        return insiderRepository.findByFullNameContainingIgnoreCase(name);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Insider> findActiveInsiders() {
        log.debug("Finding all active insiders");
        return insiderRepository.findByIsActiveTrue();
    }

    @Override
    public Insider createFromSecData(String cik, String fullName, String address) {
        log.info("Creating insider from SEC data - CIK: {}, Name: {}", cik, fullName);
        
        Insider insider = Insider.builder()
            .cik(formatCik(cik))
            .fullName(fullName)
            .insiderType(Insider.InsiderType.INDIVIDUAL)
            .isActive(true)
            .build();
        
        // Parse name components
        parseNameComponents(insider);
        
        // Parse address if provided
        if (address != null && !address.isEmpty()) {
            parseAddressComponents(insider, address);
        }
        
        return saveInsider(insider);
    }

    @Override
    public Insider updateInsiderInfo(String cik, String fullName, String address) {
        log.info("Updating insider info - CIK: {}", cik);
        
        Optional<Insider> existingInsider = findByCik(cik);
        Insider insider;
        
        if (existingInsider.isPresent()) {
            insider = existingInsider.get();
            insider.setFullName(fullName);
            parseNameComponents(insider);
            
            if (address != null && !address.isEmpty()) {
                parseAddressComponents(insider, address);
            }
        } else {
            insider = createFromSecData(cik, fullName, address);
        }
        
        return saveInsider(insider);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean insiderExists(String cik) {
        log.debug("Checking if insider exists - CIK: {}", cik);
        String formattedCik = formatCik(cik);
        return insiderRepository.existsByCik(formattedCik);
    }

    @Override
    public Insider getOrCreateInsider(String cik, String fullName, String address) {
        log.debug("Getting or creating insider - CIK: {}", cik);
        
        String formattedCik = formatCik(cik);
        Optional<Insider> existingInsider = insiderRepository.findByCik(formattedCik);
        
        if (existingInsider.isPresent()) {
            Insider insider = existingInsider.get();
            
            // Update insider information if provided data is more complete
            boolean updated = false;
            
            if (fullName != null && !fullName.equals(insider.getFullName())) {
                insider.setFullName(fullName);
                parseNameComponents(insider);
                updated = true;
            }
            
            if (address != null && !address.isEmpty() && 
                (insider.getStreetAddress1() == null || insider.getStreetAddress1().isEmpty())) {
                parseAddressComponents(insider, address);
                updated = true;
            }
            
            if (updated) {
                return saveInsider(insider);
            }
            
            return insider;
        } else {
            return createFromSecData(cik, fullName, address);
        }
    }

    @Override
    public void parseNameComponents(Insider insider) {
        log.debug("Parsing name components for: {}", insider.getFullName());
        
        if (insider.getFullName() == null || insider.getFullName().isEmpty()) {
            return;
        }
        
        String fullName = insider.getFullName().trim();
        String[] parts = fullName.split("\\s+");
        
        if (parts.length == 1) {
            // Single name
            insider.setLastName(parts[0]);
        } else if (parts.length == 2) {
            // First and last name
            insider.setFirstName(parts[0]);
            insider.setLastName(parts[1]);
        } else if (parts.length >= 3) {
            // First, middle, and last name (with possible suffix)
            insider.setFirstName(parts[0]);
            
            // Check if last part is a suffix
            String lastPart = parts[parts.length - 1].toUpperCase();
            boolean hasSuffix = lastPart.matches("(JR\\.?|SR\\.?|II|III|IV|V)");
            
            if (hasSuffix && parts.length >= 4) {
                insider.setSuffix(parts[parts.length - 1]);
                insider.setLastName(parts[parts.length - 2]);
                
                // Everything in between is middle name
                StringBuilder middleName = new StringBuilder();
                for (int i = 1; i < parts.length - 2; i++) {
                    if (middleName.length() > 0) middleName.append(" ");
                    middleName.append(parts[i]);
                }
                insider.setMiddleName(middleName.toString());
            } else {
                if (hasSuffix) {
                    insider.setSuffix(parts[parts.length - 1]);
                    insider.setLastName(parts[parts.length - 2]);
                } else {
                    insider.setLastName(parts[parts.length - 1]);
                }
                
                // Everything in between is middle name
                StringBuilder middleName = new StringBuilder();
                int endIndex = hasSuffix ? parts.length - 2 : parts.length - 1;
                for (int i = 1; i < endIndex; i++) {
                    if (middleName.length() > 0) middleName.append(" ");
                    middleName.append(parts[i]);
                }
                insider.setMiddleName(middleName.toString());
            }
        }
    }

    @Override
    public void parseAddressComponents(Insider insider, String address) {
        log.debug("Parsing address components for: {}", insider.getFullName());
        
        if (address == null || address.isEmpty()) {
            return;
        }
        
        // Simple address parsing - can be enhanced for more complex cases
        String[] lines = address.split("\\r?\\n");
        
        if (lines.length >= 1) {
            insider.setStreetAddress1(lines[0].trim());
        }
        
        if (lines.length >= 2) {
            String lastLine = lines[lines.length - 1].trim();
            
            // Try to parse city, state, zip from last line
            // Format: "City, State Zip" or "City State Zip"
            String[] cityStateZip = lastLine.split(",\\s*");
            if (cityStateZip.length == 2) {
                insider.setCity(cityStateZip[0].trim());
                
                String stateZip = cityStateZip[1].trim();
                String[] stateZipParts = stateZip.split("\\s+");
                if (stateZipParts.length >= 2) {
                    insider.setState(stateZipParts[0]);
                    insider.setZipCode(stateZipParts[1]);
                }
            } else {
                // Fallback: try to extract state and zip from end
                String[] parts = lastLine.split("\\s+");
                if (parts.length >= 3) {
                    insider.setZipCode(parts[parts.length - 1]);
                    insider.setState(parts[parts.length - 2]);
                    
                    StringBuilder city = new StringBuilder();
                    for (int i = 0; i < parts.length - 2; i++) {
                        if (city.length() > 0) city.append(" ");
                        city.append(parts[i]);
                    }
                    insider.setCity(city.toString());
                }
            }
            
            // If there's a middle line, it's probably street address 2
            if (lines.length >= 3) {
                insider.setStreetAddress2(lines[1].trim());
            }
        }
        
        // Default country to US for SEC filings
        if (insider.getCountry() == null || insider.getCountry().isEmpty()) {
            insider.setCountry("US");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Insider> findInsidersForCompany(String companyCik) {
        log.debug("Finding insiders for company CIK: {}", companyCik);
        return insiderRepository.findInsidersWithTransactionsForCompany(companyCik);
    }

    @Override
    @Transactional(readOnly = true)
    public InsiderStatistics getInsiderStatistics(String cik) {
        log.debug("Getting insider statistics for CIK: {}", cik);
        
        Optional<Insider> insiderOpt = findByCik(cik);
        if (insiderOpt.isEmpty()) {
            return new InsiderStatistics(0L, 0L, null, 0L);
        }
        
        Insider insider = insiderOpt.get();
        
        // Get transaction count
        Long totalTransactions = transactionRepository.countTransactionsByInsider(cik);
        
        // Get company count (unique companies from transactions)
        Long totalCompanies = (long) transactionRepository.findByInsiderCik(cik).stream()
            .map(t -> t.getCompany().getCik())
            .distinct()
            .toArray().length;
        
        // Get active relationships count
        Long activeRelationships = relationshipRepository.countActiveRelationshipsByInsider(cik);
        
        // Get last transaction date
        String lastTransactionDate = insider.getLastTransactionDate() != null 
            ? insider.getLastTransactionDate().toString() 
            : null;
        
        return new InsiderStatistics(totalTransactions, totalCompanies, lastTransactionDate, activeRelationships);
    }

    /**
     * Format CIK to 10-digit string with leading zeros
     */
    private String formatCik(String cik) {
        if (cik == null || cik.isEmpty()) {
            return cik;
        }
        
        try {
            long cikLong = Long.parseLong(cik);
            return String.format("%010d", cikLong);
        } catch (NumberFormatException e) {
            log.warn("Invalid CIK format: {}", cik);
            return cik;
        }
    }
}
