package org.viridexa.api.agent;

import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.MeterRegistry;

/** Low-cardinality metrics for bounded agent operations. */
@Component
public final class OperationMetrics {
    private static final Set<String> TOOLS = Set.of(
            "searchPlants", "getPlantProfile", "getGraphNeighbors",
            "getResearchSamples", "compareOilMeasurements", "getEvidenceLineage");
    private static final Set<String> STATUSES = Set.of(
            "SUCCESS", "INSUFFICIENT_EVIDENCE", "NOT_COMPARABLE", "SIDE_BY_SIDE",
            "INVALID_ARGUMENTS", "TOOL_TIMEOUT", "TOOL_FAILED", "CANCELLED", "TIMED_OUT");

    private final MeterRegistry registry;

    public OperationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordTool(String tool, String status, long durationNanos) {
        registry.timer("viridexa.agent.tool.duration",
                "tool", TOOLS.contains(tool) ? tool : "UNKNOWN",
                "status", STATUSES.contains(status) ? status : "UNKNOWN")
                .record(Math.max(0, durationNanos), TimeUnit.NANOSECONDS);
    }

    public void recordTermination(String status) {
        if ("CANCELLED".equals(status) || "TIMED_OUT".equals(status)) {
            registry.counter("viridexa.agent.termination", "outcome", status).increment();
        }
    }
}
