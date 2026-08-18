package org.jds.edgar4j.properties;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "edgar4j.distributed-workers.mobile-assist")
public class WorkerMobileAssistProperties {

    private boolean enabled = true;

    @NotNull
    private Duration recentPresenceWindow = Duration.ofSeconds(30);

    @NotNull
    private Duration initialClaimWindow = Duration.ofSeconds(12);

    @NotNull
    private Duration leasedCompletionWindow = Duration.ofSeconds(45);

    @NotNull
    private Duration pollInterval = Duration.ofMillis(250);

    @AssertTrue(message = "mobile assist durations must be positive")
    public boolean isDurationConfigurationValid() {
        return positive(recentPresenceWindow)
                && positive(initialClaimWindow)
                && positive(leasedCompletionWindow)
                && positive(pollInterval);
    }

    private static boolean positive(Duration value) {
        return value != null && !value.isZero() && !value.isNegative();
    }
}
