package org.jds.edgar4j.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.insider.Company;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface InsiderCompanyDataPort extends BaseInsiderDataPort<Company> {

    Optional<Company> findByCik(String cik);

    Optional<Company> findByTickerSymbol(String tickerSymbol);

    List<Company> findByCompanyNameContainingIgnoreCase(String name);

    List<Company> findByIsActiveTrue();

    List<Company> findByExchange(String exchange);

    List<Company> findBySector(String sector);

    List<Company> findByLastFilingDateAfter(LocalDateTime since);

    List<Company> findBySicCode(String sicCode);

    boolean existsByCik(String cik);

    boolean existsByTickerSymbol(String tickerSymbol);

    Long countActiveCompanies();

    List<Company> findCompaniesWithTransactionsSince(LocalDateTime since);
}
