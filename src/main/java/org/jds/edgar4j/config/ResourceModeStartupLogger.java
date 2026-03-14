package org.jds.edgar4j.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ResourceModeStartupLogger {

    private final ResourceModeInfo resourceModeInfo;

    public ResourceModeStartupLogger(ResourceModeInfo resourceModeInfo) {
        this.resourceModeInfo = resourceModeInfo;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logMode() {
        log.info("edgar4j resource mode: {} ({})",
                resourceModeInfo.mode(),
                resourceModeInfo.description());
    }
}
