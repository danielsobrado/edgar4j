package org.jds.edgar4j.adapter.mongo;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jds.edgar4j.port.Form13DGDataPort;
import org.jds.edgar4j.repository.Form13DGRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high & !resource-low")
@RequiredArgsConstructor
public class Form13DGMongoAdapter implements Form13DGDataPort {

    @Delegate(types = Form13DGDataPort.class)
    private final Form13DGRepository repository;
}