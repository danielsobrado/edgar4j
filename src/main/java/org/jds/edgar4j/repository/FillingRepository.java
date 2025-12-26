package org.jds.edgar4j.repository;

import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.entity.Filling;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FillingRepository extends MongoRepository<Filling, String> {

    List<Filling> findByCik(String cik);

    Page<Filling> findByCik(String cik, Pageable pageable);

    Optional<Filling> findByAccessionNumber(String accessionNumber);

    List<Filling> findByCompany(String company);

    Page<Filling> findByCompany(String company, Pageable pageable);

    @Query("{ 'formType.number': ?0 }")
    List<Filling> findByFormTypeNumber(String formTypeNumber);

    @Query("{ 'formType.number': ?0 }")
    Page<Filling> findByFormTypeNumber(String formTypeNumber, Pageable pageable);

    List<Filling> findByFillingDateBetween(Date startDate, Date endDate);

    Page<Filling> findByFillingDateBetween(Date startDate, Date endDate, Pageable pageable);

    @Query("{ 'cik': ?0, 'formType.number': ?1 }")
    Page<Filling> findByCikAndFormType(String cik, String formTypeNumber, Pageable pageable);

    @Query("{ 'fillingDate': { $gte: ?0, $lte: ?1 }, 'formType.number': { $in: ?2 } }")
    Page<Filling> searchFillings(Date startDate, Date endDate, List<String> formTypes, Pageable pageable);

    @Query(value = "{ $or: [ { 'company': { $regex: ?0, $options: 'i' } }, { 'cik': ?0 } ] }",
           fields = "{ 'id': 1, 'company': 1, 'cik': 1, 'formType': 1, 'fillingDate': 1, 'accessionNumber': 1 }")
    Page<Filling> searchByCompanyOrCik(String searchTerm, Pageable pageable);

    List<Filling> findByIsXBRLTrue();

    List<Filling> findByIsInlineXBRLTrue();

    Page<Filling> findAllByOrderByFillingDateDesc(Pageable pageable);

    long countByFormTypeNumber(String formTypeNumber);

    @Query(value = "{}", count = true)
    long countAllFillings();

    List<Filling> findTop10ByOrderByFillingDateDesc();
}
