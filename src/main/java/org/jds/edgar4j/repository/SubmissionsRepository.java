package org.jds.edgar4j.repository;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.Submissions;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SubmissionsRepository extends MongoRepository<Submissions, String> {

    Optional<Submissions> findByCik(String cik);

    Optional<Submissions> findByCompanyName(String companyName);

    List<Submissions> findByCompanyNameContainingIgnoreCase(String companyName);

    List<Submissions> findBySic(String sic);

    List<Submissions> findByStateOfIncorporation(String state);

    List<Submissions> findByEntityType(String entityType);

    @Query("{ $or: [ { 'companyName': { $regex: ?0, $options: 'i' } }, { 'cik': ?0 } ] }")
    Page<Submissions> searchByCompanyNameOrCik(String searchTerm, Pageable pageable);

    boolean existsByCik(String cik);

    List<Submissions> findByInsiderTransactionForOwnerExistsTrue();

    List<Submissions> findByInsiderTransactionForIssuerExistsTrue();
}

