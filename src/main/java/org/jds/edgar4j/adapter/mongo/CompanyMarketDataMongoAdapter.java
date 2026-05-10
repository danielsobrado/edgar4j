package org.jds.edgar4j.adapter.mongo;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jds.edgar4j.port.CompanyMarketDataDataPort;
import org.jds.edgar4j.repository.CompanyMarketDataRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class CompanyMarketDataMongoAdapter implements CompanyMarketDataDataPort {

    @Delegate(types = CompanyMarketDataDataPort.class)
    private final CompanyMarketDataRepository repository;
}
