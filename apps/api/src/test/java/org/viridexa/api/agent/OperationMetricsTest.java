package org.viridexa.api.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class OperationMetricsTest {
    @Test
    void toolAndStatusLabelsAreBoundedAndTerminationCountsOnlyTerminalOutcomes() {
        var registry = new SimpleMeterRegistry();
        var metrics = new OperationMetrics(registry);

        metrics.recordTool("searchPlants", "SUCCESS", 1_000_000);
        metrics.recordTool("attacker-controlled-name", "opaque-user-value", 2_000_000);
        metrics.recordTermination("CANCELLED");
        metrics.recordTermination("TIMED_OUT");
        metrics.recordTermination("COMPLETED");

        assertEquals(1, registry.get("viridexa.agent.tool.duration")
                .tags("tool", "searchPlants", "status", "SUCCESS").timer().count());
        assertEquals(1, registry.get("viridexa.agent.tool.duration")
                .tags("tool", "UNKNOWN", "status", "UNKNOWN").timer().count());
        assertEquals(1, registry.get("viridexa.agent.termination")
                .tag("outcome", "CANCELLED").counter().count());
        assertEquals(1, registry.get("viridexa.agent.termination")
                .tag("outcome", "TIMED_OUT").counter().count());
        assertEquals(2, registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals("viridexa.agent.termination")).count());
    }
}
