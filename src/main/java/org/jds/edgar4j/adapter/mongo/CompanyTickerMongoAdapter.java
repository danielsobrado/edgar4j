package org.jds.edgar4j.adapter.mongo;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jds.edgar4j.port.CompanyTickerDataPort;
import org.jds.edgar4j.repository.CompanyTickerRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class CompanyTickerMongoAdapter implements CompanyTickerDataPort {

    @Delegate(types = CompanyTickerDataPort.class)
    private final CompanyTickerRepository repository;
}