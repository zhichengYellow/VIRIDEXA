package org.viridexa.api.science;

import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/v1/science")
public class ScientificController {
    private final ScientificService service;
    public ScientificController(ScientificService service) { this.service=service; }
    @GetMapping("/facts")
    public List<Map<String,Object>> facts(@RequestParam(required=false) String pipeline,
            @RequestParam(defaultValue="30") int limit) {
        try { return service.facts(pipeline, limit); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
    }
    @GetMapping("/lineage/{id}")
    public List<Map<String,Object>> lineage(@PathVariable String id) { return service.lineage(id); }
    @GetMapping("/plants")
    public List<ScientificService.Fact> plants(@RequestParam(required=false) String q,
            @RequestParam(defaultValue="30") int limit) { return service.plantSearch(q,limit); }
    @GetMapping("/coverage")
    public List<ScientificService.Coverage> coverage() { return service.coverage(); }
    @GetMapping("/lineage/{id}/source")
    public List<Map<String,Object>> sourceChain(@PathVariable String id) { return service.sourceChain(id); }
    @GetMapping("/measurements")
    public List<ScientificService.SampleComparison> measurements(@RequestParam(required=false) String kind,
            @RequestParam(required=false) String sampleId, @RequestParam(required=false) String basis,
            @RequestParam(required=false) String sourceId, @RequestParam(defaultValue="30") int limit) {
        try { return service.measurements(kind,sampleId,basis,sourceId,limit); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,e.getMessage()); }
    }

    @GetMapping("/morphology")
    public List<Map<String,Object>> morphology(@RequestParam(required=false) String leafShape,
        @RequestParam(required=false) String leafMargin,@RequestParam(required=false) String flowerColour,
        @RequestParam(required=false) String growthForm,@RequestParam(required=false) String fruitType) {
        return service.morphology(leafShape,leafMargin,flowerColour,growthForm,fruitType);
    }
    @GetMapping("/lineage/{id}/documents")
    public List<Map<String,Object>> documents(@PathVariable String id) { return service.documents(id); }
    @GetMapping("/relationships")
    public List<ScientificService.GraphEdge> relationships() { return service.relationships(); }
}
