package org.jds.edgar4j.properties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jds.edgar4j.model.WorkerSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "edgar4j.distributed-workers")
public class DistributedWorkerProperties {

    private boolean enabled = false;

    @Valid
    @NotNull
    private Coordinator coordinator = new Coordinator();

    @Valid
    @NotNull
    private Artifact artifact = new Artifact();

    @Valid
    @NotNull
    private ServerWorker serverWorker = new ServerWorker();

    @Valid
    @NotNull
    private SourcePolicy sourcePolicy = new SourcePolicy();

    @Data
    public static class Coordinator {
        @NotNull
        private Duration leaseDuration = Duration.ofMinutes(5);

        @NotNull
        private Duration heartbeatExtension = Duration.ofMinutes(5);

        @Min(1)
        private int maxAttempts = 3;

        @NotNull
        private Duration retryBackoff = Duration.ofSeconds(30);

        @NotNull
        private Duration retryBackoffMax = Duration.ofMinutes(15);

        @Min(1)
        private int maxLeaseBatch = 2;

        @NotNull
        private Duration idleRetryMin = Duration.ofSeconds(5);

        @NotNull
        private Duration idleRetryMax = Duration.ofSeconds(60);

        @NotNull
        private Duration sessionDuration = Duration.ofMinutes(30);

        @NotNull
        private Duration maintenanceInterval = Duration.ofSeconds(30);

        @AssertTrue(message = "distributed worker coordinator durations must be positive and ordered")
        public boolean isDurationConfigurationValid() {
            return isPositive(leaseDuration)
                    && isPositive(heartbeatExtension)
                    && isPositive(retryBackoff)
                    && isPositive(retryBackoffMax)
                    && isPositive(idleRetryMin)
                    && isPositive(idleRetryMax)
                    && isPositive(sessionDuration)
                    && isPositive(maintenanceInterval)
                    && !retryBackoffMax.minus(retryBackoff).isNegative()
                    && !idleRetryMax.minus(idleRetryMin).isNegative();
        }
    }

    @Data
    public static class Artifact {
        @NotNull
        private DataSize maxMobileBytes = DataSize.ofMegabytes(50);

        @NotNull
        private Duration stagingRetention = Duration.ofHours(1);

        @AssertTrue(message = "distributed worker artifact limits must be positive")
        public boolean isArtifactConfigurationValid() {
            return maxMobileBytes != null
                    && maxMobileBytes.toBytes() > 0
                    && isPositive(stagingRetention);
        }
    }

    @Data
    public static class ServerWorker {
        private boolean enabled = true;

        @Min(1)
        @Max(5)
        private int maxConcurrency = 4;

        @NotNull
        private Duration pollInterval = Duration.ofSeconds(2);

        @AssertTrue(message = "server worker poll interval must be positive")
        public boolean isPollIntervalValid() {
            return isPositive(pollInterval);
        }
    }

    @Data
    public static class SourcePolicy {
        @NotEmpty
        private List<String> allowedHosts = new ArrayList<>(List.of(
                "www.sec.gov",
                "data.sec.gov",
                "efts.sec.gov"));

        @NotEmpty
        private Set<WorkerSource> mobileEligibleSources = EnumSet.of(WorkerSource.SEC_EDGAR);

        @Min(0)
        private int maxRedirects = 3;
    }

    private static boolean isPositive(Duration duration) {
        return duration != null && !duration.isZero() && !duration.isNegative();
    }
}
