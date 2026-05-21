package org.jds.edgar4j.adapter.mongo;

import org.jds.edgar4j.port.PoliticalTradeDataPort;
import org.jds.edgar4j.repository.PoliticalTradeRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class PoliticalTradeMongoAdapter implements PoliticalTradeDataPort {

    @Delegate(types = PoliticalTradeDataPort.class)
    private final PoliticalTradeRepository repository;
}
