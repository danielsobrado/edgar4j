package org.jds.edgar4j.adapter.mongo;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jds.edgar4j.port.Form8KDataPort;
import org.jds.edgar4j.repository.Form8KRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class Form8KMongoAdapter implements Form8KDataPort {

    @Delegate(types = Form8KDataPort.class)
    private final Form8KRepository repository;
}
