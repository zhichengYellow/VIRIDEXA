package org.viridexa.api.plant;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/plants")
public class PlantController {
    private final PlantService service;

    public PlantController(PlantService service) {
        this.service = service;
    }

    @GetMapping
    public PlantSearchResult search(
            @RequestParam(required = false) @Size(max = 120) String query,
            @RequestParam(defaultValue = "3") @Min(1) @Max(50) int limit,
            @RequestParam(defaultValue = "0") @Min(0) long afterId) {
        return afterId==0?service.search(query, limit):service.searchAfter(query,limit,afterId);
    }

    @GetMapping("/{id}")
    public PlantRecord findById(@PathVariable @Positive long id) {
        return service.getById(id);
    }
}
