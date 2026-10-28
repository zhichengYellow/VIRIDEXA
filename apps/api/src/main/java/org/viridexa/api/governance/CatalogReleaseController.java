package org.viridexa.api.governance;
import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
@Validated
@RequestMapping("/api/v1/admin/science/catalog")
public class CatalogReleaseController {
 private final CatalogReleaseService service;
 public CatalogReleaseController(CatalogReleaseService service){this.service=service;}
 @GetMapping public List<Map<String,Object>> list(){return service.list();}
 @GetMapping("/{id}") public Map<String,Object> detail(@PathVariable @Min(1) long id){return service.detail(id);}
 @PostMapping("/{id}/decisions") public Map<String,Object> decide(@PathVariable @Min(1) long id,@Valid @RequestBody CatalogReleaseService.Command command,Authentication auth){return service.decide(id,command,auth);}
}
