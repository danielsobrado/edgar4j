package org.jds.edgar4j.adapter.mongo;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jds.edgar4j.port.Form20FDataPort;
import org.jds.edgar4j.repository.Form20FRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class Form20FMongoAdapter implements Form20FDataPort {

    @Delegate(types = Form20FDataPort.class)
    private final Form20FRepository repository;
}
