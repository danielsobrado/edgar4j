package org.jds.edgar4j.port;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Filling;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface FillingDataPort extends BaseDocumentDataPort<Filling> {

    Optional<Filling> findByAccessionNumber(String accessionNumber);

    Page<Filling> findByCik(String cik, Pageable pageable);

    Page<Filling> findByCompany(String company, Pageable pageable);

    Page<Filling> findByFormTypeNumber(String formTypeNumber, Pageable pageable);

    Page<Filling> findByCikAndFormType(String cik, String formTypeNumber, Pageable pageable);

    Page<Filling> findByCikAndFormTypeIn(String cik, List<String> formTypeNumbers, Pageable pageable);

    Page<Filling> findByCikAndFormTypeAndFillingDateBetween(
            String cik,
            String formTypeNumber,
            Date startDate,
            Date endDate,
            Pageable pageable);

    Page<Filling> findByCikIn(List<String> ciks, Pageable pageable);

    Page<Filling> findByCikInAndFormType(List<String> ciks, String formTypeNumber, Pageable pageable);

    Page<Filling> findByCikInAndFormTypeIn(List<String> ciks, List<String> formTypeNumbers, Pageable pageable);

    Page<Filling> searchFillings(Date startDate, Date endDate, List<String> formTypes, Pageable pageable);

    Page<Filling> searchByCompanyOrCik(String searchTerm, Pageable pageable);

    Page<Filling> findAllByOrderByFillingDateDesc(Pageable pageable);

    List<Filling> findTop10ByOrderByFillingDateDesc();

    long countByFormTypeNumber(String formTypeNumber);

    Page<Filling> findRecentXbrlFilingsByCik(String cik, Pageable pageable);
}
