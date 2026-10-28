package org.viridexa.api.workbench;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.CacheControl;
import jakarta.servlet.http.HttpServletResponse;
import static org.viridexa.api.workbench.WorkbenchContract.*;
@RestController
@RequestMapping("/api/v1/workbench")

public class WorkbenchController {
 private final WorkbenchService service;
 public WorkbenchController(WorkbenchService service){this.service=service;}
 @ModelAttribute void cache(HttpServletResponse response){response.setHeader("Cache-Control","no-store");}
 @GetMapping("/overview") public Overview overview(){return service.overview();}
 @GetMapping("/plants") public List<Plant> plants(@RequestParam(required=false) String q,@RequestParam(defaultValue="20") int limit){return service.plants(q,limit);}
 @GetMapping("/plants/{id}") public Map<String,Object> plant(@PathVariable long id){return service.plant(id);}
 @GetMapping("/samples") public Page<Sample> samples(@RequestParam(required=false) String q,@RequestParam(required=false) Long plantId,@RequestParam(required=false) String sourceId,@RequestParam(required=false) String kind,@RequestParam(required=false) String plantPart,@RequestParam(required=false) String status,@RequestParam(required=false) Boolean unlinked,@RequestParam(defaultValue="20") int limit,@RequestParam(required=false) String cursor){return service.samples(new WorkbenchRepository.Filters(q,plantId,sourceId,kind,plantPart,status,unlinked),limit,cursor);}
 @GetMapping("/points") public Page<Point> points(@RequestParam List<String> sampleIds,@RequestParam(required=false) String kind,@RequestParam(defaultValue="60") int limit,@RequestParam(required=false) String cursor){return service.points(sampleIds,kind,limit,cursor);}
 @GetMapping("/points/{id}/evidence") public Evidence evidence(@PathVariable String id){return service.evidence(id);}
 @PostMapping("/compare") public Comparison compare(@RequestBody Selection selection){return service.compare(selection);}
}
