package org.jds.edgar4j.port;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Submissions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SubmissionsDataPort extends BaseDocumentDataPort<Submissions> {

    Optional<Submissions> findByCik(String cik);

    Page<Submissions> searchByCompanyNameOrCik(String searchTerm, Pageable pageable);

    List<Submissions> findByTickersContaining(String ticker);
}
