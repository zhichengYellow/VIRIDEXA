package org.viridexa.api.governance;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/science")
public class ReviewController {
 private final ReviewService service;
 public ReviewController(ReviewService service){this.service=service;}
 @PostMapping("/candidates/{id}/decisions")
 public ReviewService.Result decide(@PathVariable String id,@Valid @RequestBody ReviewService.Command command,Authentication auth){return service.decide(id,command,auth);}
 @PostMapping("/sources/{id}/license")
 public ReviewService.Result license(@PathVariable String id,@Valid @RequestBody ReviewService.LicenseCommand command,Authentication auth){return service.license(id,command,auth);}
}
