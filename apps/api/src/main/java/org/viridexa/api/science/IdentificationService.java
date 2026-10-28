package org.viridexa.api.science;
import java.util.*;
import org.springframework.stereotype.Service;

/** Unknown terms cannot be used as negative evidence; only published exact concepts participate. */
@Service
public class IdentificationService {
 private final ScientificMapper mapper;
 public IdentificationService(ScientificMapper mapper){this.mapper=mapper;}
 private static final Map<String,Set<String>> CONCEPTS=Map.of(
  "leaf_shape",Set.of("OVATE","ELLIPTIC","LINEAR","LANCEOLATE","OBLONG"),
  "leaf_margin",Set.of("ENTIRE","SERRATE","DENTATE","CRENATE","LOBED"),
  "flower_colour",Set.of("WHITE","YELLOW","RED","PINK","PURPLE","BLUE","GREEN"),
  "growth_form",Set.of("TREE","SHRUB","HERB","VINE","CLIMBER","WOODY"),
  "fruit_type",Set.of("BERRY","DRUPE","CAPSULE","LEGUME","ACHENE","NUT","FOLLICLE","CONE"));
 public record Result(String status,List<Map<String,Object>> matches,List<String> insufficientEvidenceCandidateIds,
  Map<String,String> ignoredUnknownCriteria,boolean truncated,String vocabularyVersion){}
 public Result identify(Map<String,String> criteria){
  Map<String,String> known=new LinkedHashMap<>(),ignored=new LinkedHashMap<>();
  criteria.forEach((field,value)->{if(value!=null&&!value.isBlank()){String concept=value.trim().toUpperCase(Locale.ROOT);if(CONCEPTS.getOrDefault(field,Set.of()).contains(concept))known.put(field,concept);else ignored.put(field,value);}});
  if(known.isEmpty())return new Result("EVIDENCE_INSUFFICIENT",List.of(),List.of(),ignored,false,"lexical-vocabulary-v1");
  var rows=mapper.identificationRecords();boolean truncated=rows.size()>1000;var matches=new ArrayList<Map<String,Object>>();var insufficient=new ArrayList<String>();
  for(var row:rows.stream().limit(1000).toList()){
   var projection=PublicScientificData.object(row.get("projection"));var traits=PublicScientificData.object(projection.get("traits"));boolean conflict=false,unknown=false;
   for(var criterion:known.entrySet()){
    Object values=traits.get(criterion.getKey());boolean matched=false,hasUnknown=false,hasKnown=false;
    if(values instanceof List<?> list){for(var item:list){Object concept=PublicScientificData.object(item).get("concept");if(concept==null)hasUnknown=true;else{hasKnown=true;if(criterion.getValue().equals(concept))matched=true;}}}
    if(!matched){if(!hasKnown||hasUnknown)unknown=true;else conflict=true;}
   }
   if(!conflict){if(unknown)insufficient.add(row.get("id").toString());else{var match=new LinkedHashMap<String,Object>();match.put("factId",row.get("id"));match.put("knowledgeConfidence",row.get("knowledge_confidence"));match.put("notice","来源特征匹配，不代表人工鉴定结论");match.put("plant",PublicScientificData.payload(row.get("normalized_payload")));matches.add(match);}}
  }
  String state=!matches.isEmpty()?"MATCHES":rows.isEmpty()||!insufficient.isEmpty()||truncated?"EVIDENCE_INSUFFICIENT":"NO_MATCH";
  return new Result(state,matches,insufficient,ignored,truncated,"lexical-vocabulary-v1");
 }
}
