package org.jds.edgar4j.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

class ResourceModeProfileActivatorTest {

    private final ResourceModeProfileActivator activator = new ResourceModeProfileActivator();

    @Test
    void activatesLowProfileWhenModeIsLow() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("edgar4j.resource-mode", "low");

        activator.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getActiveProfiles()).containsExactly("resource-low");
    }

    @Test
    void defaultsToHighProfileWhenModeIsMissing() {
        MockEnvironment environment = new MockEnvironment();

        activator.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getActiveProfiles()).containsExactly("resource-high");
    }

    @Test
    void preservesExistingExplicitProfiles() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test-low");

        activator.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getActiveProfiles()).containsExactly("test-low", "resource-low");
    }

    @Test
    void lowProfileWinsWhenBothResourceSignalsPresent() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("test", "test-low");

        activator.postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getActiveProfiles()).containsExactly("test", "test-low", "resource-low");
    }
}
