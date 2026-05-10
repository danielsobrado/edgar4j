package org.jds.edgar4j.adapter.mongo;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jds.edgar4j.port.CompanyDataPort;
import org.jds.edgar4j.repository.CompanyRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class CompanyMongoAdapter implements CompanyDataPort {

    @Delegate(types = CompanyDataPort.class)
    private final CompanyRepository repository;
}