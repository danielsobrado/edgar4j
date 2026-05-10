package org.jds.edgar4j.adapter.mongo;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jds.edgar4j.port.Form6KDataPort;
import org.jds.edgar4j.repository.Form6KRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class Form6KMongoAdapter implements Form6KDataPort {

    @Delegate(types = Form6KDataPort.class)
    private final Form6KRepository repository;
}
