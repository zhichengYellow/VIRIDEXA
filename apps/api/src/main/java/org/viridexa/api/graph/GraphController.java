package org.viridexa.api.graph;
import org.springframework.web.bind.annotation.*;
import static org.viridexa.api.graph.GraphContract.*;
@RestController
@RequestMapping("/api/v1/graph")
public class GraphController {
 private final GraphService service;
 public GraphController(GraphService service){this.service=service;}
 @GetMapping("/search") public Page search(@RequestParam(defaultValue="") String q,@RequestParam(defaultValue="12") int limit,@RequestParam(required=false) String cursor){return service.search(q,limit,cursor);}
 @GetMapping("/nodes/{id}") public Node node(@PathVariable String id){return service.detail(id);}
 @GetMapping("/nodes/{id}/neighbors") public Page neighbors(@PathVariable String id,@RequestParam(required=false) String relationType,@RequestParam(required=false) String nodeType,@RequestParam(required=false) String verificationState,@RequestParam(required=false) String publicationState,@RequestParam(defaultValue="20") int limit,@RequestParam(required=false) String cursor,@RequestParam(defaultValue="1") int depth){return service.neighbors(id,relationType,nodeType,verificationState,publicationState,limit,cursor,depth);}
 @GetMapping("/meta") public Meta meta(){return service.meta();}
}
