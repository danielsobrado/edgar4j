package org.jds.edgar4j.config;

import java.util.Arrays;
import java.util.Locale;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

public class ResourceModeProfileActivator implements EnvironmentPostProcessor, Ordered {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String derivedProfile = deriveProfile(environment);
        if (derivedProfile == null) {
            return;
        }

        if (!Arrays.asList(environment.getActiveProfiles()).contains(derivedProfile)) {
            environment.addActiveProfile(derivedProfile);
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }

    private String deriveProfile(ConfigurableEnvironment environment) {
        String[] activeProfiles = environment.getActiveProfiles();
        if (activeProfiles.length == 0) {
            return resolveProfile(environment.getProperty("edgar4j.resource-mode", "high"));
        }

        for (String activeProfile : activeProfiles) {
            String resolvedProfile = resolveProfile(activeProfile);
            if (resolvedProfile != null) {
                return resolvedProfile;
            }
        }

        return resolveProfile(environment.getProperty("edgar4j.resource-mode", "high"));
    }

    private String resolveProfile(String value) {
        String normalizedValue = value == null ? "high" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalizedValue) {
            case "resource-low", "low", "test-low" -> "resource-low";
            case "resource-high", "high", "test" -> "resource-high";
            default -> null;
        };
    }
}