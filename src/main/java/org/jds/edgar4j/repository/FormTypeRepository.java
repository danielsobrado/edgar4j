package org.jds.edgar4j.repository;

import java.util.List;
import java.util.Optional;

import org.jds.edgar4j.model.FormType;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FormTypeRepository extends MongoRepository<FormType, String> {

    Optional<FormType> findByNumber(String number);

    List<FormType> findByNumberIn(List<String> numbers);

    List<FormType> findByDescriptionContainingIgnoreCase(String description);

    @Query("{ $or: [ { 'number': { $regex: ?0, $options: 'i' } }, { 'description': { $regex: ?0, $options: 'i' } } ] }")
    List<FormType> searchByNumberOrDescription(String searchTerm);

    boolean existsByNumber(String number);
}

