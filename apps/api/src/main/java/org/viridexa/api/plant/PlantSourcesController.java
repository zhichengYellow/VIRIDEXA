package org.viridexa.api.plant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.viridexa.api.science.PublicScientificData;

@RestController
@RequestMapping("/api/v1/plants")
public class PlantSourcesController {
 private final PlantService plants;private final JdbcTemplate jdbc;
 public PlantSourcesController(PlantService plants,JdbcTemplate jdbc){this.plants=plants;this.jdbc=jdbc;}
 @GetMapping("/{id}/sources") public Map<String,Object> sources(@PathVariable long id){
  var plant=plants.getById(id); // Same public identity boundary as every other module.
  var names=jdbc.queryForList("SELECT n.name_text,n.name_role,n.language_tag,n.review_state,a.source_url,a.source_version,a.authority_taxon_id,a.match_method,a.confidence,a.queried_at,na.sha256 AS name_source_sha256,JSON_UNQUOTE(JSON_EXTRACT(ne.locator,'$.sheet')) AS name_source_sheet,CAST(JSON_EXTRACT(ne.locator,'$.row') AS UNSIGNED) AS name_source_row FROM plant_name n LEFT JOIN botanical_name_assertion a ON a.plant_name_id=n.id LEFT JOIN evidence ne ON ne.id=n.evidence_id LEFT JOIN source_asset na ON na.id=ne.source_asset_id WHERE n.plant_id=? AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED') ORDER BY n.id LIMIT 40",id);
  var taxonomy=jdbc.queryForList("SELECT t.id,t.canonical_name,t.taxon_rank,t.identity_state FROM plant p JOIN taxon t ON t.id=p.taxon_id WHERE p.id=? "
   +"UNION SELECT parent.id,parent.canonical_name,parent.taxon_rank,parent.identity_state FROM plant p JOIN taxon leaf ON leaf.id=p.taxon_id JOIN taxon parent ON parent.id=leaf.parent_id WHERE p.id=? "
   +"UNION SELECT ancestor.id,ancestor.canonical_name,ancestor.taxon_rank,ancestor.identity_state FROM plant p JOIN taxon leaf ON leaf.id=p.taxon_id JOIN taxon parent ON parent.id=leaf.parent_id JOIN taxon ancestor ON ancestor.id=parent.parent_id WHERE p.id=? ORDER BY id LIMIT 3",id,id,id);
  // Exact names retain their original assertions; this is source lookup, not identity resolution.
  var records=jdbc.queryForList("SELECT f.id,f.pipeline,f.knowledge_confidence,f.normalized_payload,f.source_id,f.locator FROM scientific_candidate c JOIN public_source_record f ON f.id=c.id WHERE c.graph_scientific_name=LEFT(?,191) COLLATE utf8mb4_bin AND JSON_UNQUOTE(JSON_EXTRACT(f.normalized_payload,'$.scientific_name'))=? ORDER BY f.pipeline,f.id LIMIT 30",plant.scientificName(),plant.scientificName());
  return Map.of("names",names,"taxonomy",taxonomy,"records",PublicScientificData.rows(records),"notice","按原文学名精确检索的来源记录；资料记载和自动提取均不代表具名人工核验结论，未关联样本不冒认物种。");
 }
}
