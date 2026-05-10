package org.jds.edgar4j.adapter.mongo;

import lombok.RequiredArgsConstructor;
import lombok.experimental.Delegate;
import org.jds.edgar4j.port.AppSettingsDataPort;
import org.jds.edgar4j.repository.AppSettingsRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("resource-high")
@RequiredArgsConstructor
public class AppSettingsMongoAdapter implements AppSettingsDataPort {

    @Delegate(types = AppSettingsDataPort.class)
    private final AppSettingsRepository repository;
}