package org.jds.edgar4j.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
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

        LinkedHashSet<String> activeProfiles = new LinkedHashSet<>(Arrays.asList(environment.getActiveProfiles()));
        activeProfiles.remove("resource-high");
        activeProfiles.remove("resource-low");
        if (!activeProfiles.contains(derivedProfile)) {
            activeProfiles.add(derivedProfile);
        }
        environment.setActiveProfiles(activeProfiles.toArray(String[]::new));
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

        boolean hasLowProfile = Arrays.stream(activeProfiles)
                .anyMatch(this::isLowProfile);
        if (hasLowProfile) {
            return "resource-low";
        }

        boolean hasHighProfile = Arrays.stream(activeProfiles)
                .anyMatch(this::isHighProfile);
        if (hasHighProfile) {
            return "resource-high";
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

    private boolean isLowProfile(String profile) {
        String normalizedProfile = profile == null ? null : profile.trim().toLowerCase(Locale.ROOT);
        return "resource-low".equals(normalizedProfile) || "low".equals(normalizedProfile)
                || "test-low".equals(normalizedProfile);
    }

    private boolean isHighProfile(String profile) {
        String normalizedProfile = profile == null ? null : profile.trim().toLowerCase(Locale.ROOT);
        return "resource-high".equals(normalizedProfile) || "high".equals(normalizedProfile)
                || "test".equals(normalizedProfile);
    }
}
