package org.viridexa.api.science;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/science")
public class IdentificationController {
 private final IdentificationService service;
 public IdentificationController(IdentificationService service){this.service=service;}
 @GetMapping("/identify") public IdentificationService.Result identify(@RequestParam(required=false) String leafShape,
  @RequestParam(required=false) String leafMargin,@RequestParam(required=false) String flowerColour,
  @RequestParam(required=false) String growthForm,@RequestParam(required=false) String fruitType){
  Map<String,String> query=new LinkedHashMap<>();query.put("leaf_shape",leafShape);query.put("leaf_margin",leafMargin);query.put("flower_colour",flowerColour);query.put("growth_form",growthForm);query.put("fruit_type",fruitType);return service.identify(query);
 }
}
