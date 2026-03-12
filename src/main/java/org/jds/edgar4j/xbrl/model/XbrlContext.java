package org.jds.edgar4j.xbrl.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents an XBRL context defining the entity, period, and dimensions
 * for associated facts.
 */
@Data
@Builder
public class XbrlContext {

    private String id;

    // Entity information
    private String entityIdentifier;
    private String entityScheme;

    // Period information
    private XbrlPeriod period;

    // Dimensional information (segments/scenarios)
    @Builder.Default
    private List<XbrlDimension> dimensions = new ArrayList<>();

    /**
     * Check if this context has any explicit dimensions.
     */
    public boolean hasDimensions() {
        return dimensions != null && !dimensions.isEmpty();
    }

    /**
     * Get dimension value by axis name.
     */
    public String getDimensionValue(String axisName) {
        if (dimensions == null) return null;
        return dimensions.stream()
                .filter(d -> d.getAxisLocalName().equals(axisName))
                .map(XbrlDimension::getMemberLocalName)
                .findFirst()
                .orElse(null);
    }

    /**
     * Check if this is an instant context (point in time).
     */
    public boolean isInstant() {
        return period != null && period.isInstant();
    }

    /**
     * Check if this is a duration context (period of time).
     */
    public boolean isDuration() {
        return period != null && period.isDuration();
    }

    /**
     * Get a human-readable description of this context.
     */
    public String getDescription() {
        StringBuilder sb = new StringBuilder();

        if (period != null) {
            if (period.isInstant()) {
                sb.append("As of ").append(period.getInstant());
            } else if (period.isDuration()) {
                sb.append(period.getStartDate()).append(" to ").append(period.getEndDate());
            } else {
                sb.append("Forever");
            }
        }

        if (hasDimensions()) {
            sb.append(" [");
            for (int i = 0; i < dimensions.size(); i++) {
                if (i > 0) sb.append(", ");
                XbrlDimension dim = dimensions.get(i);
                sb.append(dim.getAxisLocalName()).append("=").append(dim.getMemberLocalName());
            }
            sb.append("]");
        }

        return sb.toString();
    }

    @Data
    @Builder
    public static class XbrlPeriod {
        private LocalDate instant;    // For instant periods
        private LocalDate startDate;  // For duration periods
        private LocalDate endDate;    // For duration periods
        private boolean isForever;    // For forever/context-free

        public boolean isInstant() {
            return instant != null;
        }

        public boolean isDuration() {
            return startDate != null && endDate != null;
        }

        /**
         * Get the effective end date (instant or end date).
         */
        public LocalDate getEndDate() {
            return instant != null ? instant : endDate;
        }
    }

    @Data
    @Builder
    public static class XbrlDimension {
        private String axisNamespace;
        private String axisLocalName;
        private String memberNamespace;
        private String memberLocalName;
        private boolean isTyped;      // Typed dimension vs explicit dimension
        private String typedValue;    // Value for typed dimensions
    }
}
