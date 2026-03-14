package org.jds.edgar4j.port;

import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.ListPagingAndSortingRepository;
import org.springframework.data.repository.NoRepositoryBean;

@NoRepositoryBean
public interface BaseInsiderDataPort<T> extends ListCrudRepository<T, Long>, ListPagingAndSortingRepository<T, Long> {
}