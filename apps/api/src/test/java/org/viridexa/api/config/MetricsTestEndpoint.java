package org.viridexa.api.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MetricsTestEndpoint {
    @GetMapping("/actuator/metrics")
    String metrics() { return "mock metrics endpoint"; }
}
