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
@ConfigurationProperties(prefix = "edgar4j.distributed-workers.pilot")
public class WorkerPilotProperties {

    @NotNull
    private Duration tickerFreshness = Duration.ofMinutes(15);

    @AssertTrue(message = "worker pilot freshness must be positive")
    public boolean isFreshnessValid() {
        return tickerFreshness != null && !tickerFreshness.isZero() && !tickerFreshness.isNegative();
    }
}
