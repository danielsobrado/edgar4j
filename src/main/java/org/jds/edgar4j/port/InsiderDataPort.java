package org.jds.edgar4j.port;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.insider.Insider;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface InsiderDataPort extends BaseInsiderDataPort<Insider> {

    Optional<Insider> findByCik(String cik);

    List<Insider> findByFullNameContainingIgnoreCase(String name);

    List<Insider> findByLastNameIgnoreCase(String lastName);

    List<Insider> findByIsActiveTrue();

    List<Insider> findByInsiderType(Insider.InsiderType insiderType);

    List<Insider> findByLastTransactionDateAfter(LocalDateTime since);

    List<Insider> findByCityIgnoreCase(String city);

    List<Insider> findByStateIgnoreCase(String state);

    boolean existsByCik(String cik);

    Long countActiveInsiders();

    List<Insider> findInsidersWithTransactionsForCompany(String cik);

    List<Insider> findInsidersWithActiveRelationships();
}
