package org.jds.edgar4j.properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class DistributedWorkerPropertiesTest {

    @Test
    void defaultConfigurationIsValid() {
        DistributedWorkerProperties properties = new DistributedWorkerProperties();

        assertTrue(violations(properties).isEmpty());
    }

    @Test
    void invalidLeaseAndRetryConfigurationIsRejected() {
        DistributedWorkerProperties properties = new DistributedWorkerProperties();
        properties.getCoordinator().setMaxAttempts(0);
        properties.getCoordinator().setLeaseDuration(Duration.ZERO);

        assertFalse(violations(properties).isEmpty());
    }

    private static java.util.Set<jakarta.validation.ConstraintViolation<DistributedWorkerProperties>> violations(
            DistributedWorkerProperties properties) {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            return validator.validate(properties);
        }
    }
}
