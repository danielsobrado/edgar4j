package org.jds.edgar4j.adapter.mongo;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jds.edgar4j.port.Form13FDataPort;
import org.jds.edgar4j.repository.Form13FRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class Form13FMongoAdapter implements Form13FDataPort {

    @Delegate(types = Form13FDataPort.class)
    private final Form13FRepository repository;
}