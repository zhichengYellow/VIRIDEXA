package org.viridexa.api.agent;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.viridexa.api.plant.PlantService;
import org.viridexa.api.science.ScientificService;
import org.viridexa.api.graph.GraphService;

class ResearchToolsTest {
 private final PlantService plants=mock(PlantService.class);
 private final ScientificService science=mock(ScientificService.class);
 private final GraphService graph=mock(GraphService.class);
 private final ResearchTools tools=new ResearchTools(plants,science,graph);
 @Test void exposesExactlySixStrictFunctionSchemas(){
  var schemas=tools.schemas();assertEquals(6,schemas.size());
  Set<String> names=new HashSet<>();for(var s:schemas){assertEquals("function",s.get("type"));var fn=(Map<?,?>)s.get("function");names.add((String)fn.get("name"));var params=(Map<?,?>)fn.get("parameters");assertEquals(false,params.get("additionalProperties"));}
  assertEquals(Set.of("searchPlants","getPlantProfile","getGraphNeighbors","getResearchSamples","compareOilMeasurements","getEvidenceLineage"),names);
  var byName=new HashMap<String,Map<?,?>>();for(var s:schemas)byName.put((String)((Map<?,?>)s.get("function")).get("name"),(Map<?,?>)((Map<?,?>)((Map<?,?>)s.get("function")).get("parameters")));
  assertFalse(((List<?>)byName.get("getResearchSamples").get("required")).contains("sampleId"));
  assertTrue(((List<?>)byName.get("getEvidenceLineage").get("required")).contains("recordId"));
 }
 @Test void rejectsUnexpectedIdentityScopeAndInvalidBoundsBeforeCallingServices(){
  var out=tools.execute("searchPlants",Map.of("query","camellia","userId",7));
  assertEquals("INVALID_ARGUMENTS",out.status());
  assertEquals("INVALID_ARGUMENTS",tools.execute("getGraphNeighbors",Map.of("nodeId","plant:1","limit",11)).status());
  assertEquals("INVALID_ARGUMENTS",tools.execute("getResearchSamples",Map.of("sampleId","UNLINKED","kind","OIL_CONTENT")).status());
  verifyNoInteractions(plants,science,graph);
 }
 @Test void comparisonRequiresDistinctShaIdsAndFiltersExactMeasurementCompatibility(){
  String a="a".repeat(64),b="b".repeat(64);
  var duplicate=tools.execute("compareOilMeasurements",Map.of("sampleIds",List.of(a,a),"kind","OIL_CONTENT","basis","dry","unit","%"));
  assertEquals("INVALID_ARGUMENTS",duplicate.status());
  var args=Map.<String,Object>of("sampleIds",List.of(a,b),"kind","OIL_CONTENT","basis","dry","unit","%","analyte","oleic");
  when(science.measurements("OIL_CONTENT",a,"dry",null,10)).thenReturn(List.of(row(a,"OIL_CONTENT","dry","%","oleic","e1")));
  when(science.measurements("OIL_CONTENT",b,"dry",null,10)).thenReturn(List.of(row(b,"OIL_CONTENT","wet","%","oleic","e2"),row(b,"OIL_CONTENT","dry","mg","oleic","e3")));
  var result=tools.execute("compareOilMeasurements",args);
  assertEquals("NOT_COMPARABLE",result.status());assertTrue(result.records().isEmpty());
 }
 @Test void comparisonReturnsTwoActualSameAnalyteOilMeasurements(){
  String a="a".repeat(64),b="b".repeat(64);
  when(science.measurements("OIL_FA_COMPOSITION",a,"dry",null,10)).thenReturn(List.of(row(a,"OIL_FA_COMPOSITION","dry","%","oleic","ev-a")));
  when(science.measurements("OIL_FA_COMPOSITION",b,"dry",null,10)).thenReturn(List.of(row(b,"OIL_FA_COMPOSITION","dry","%","oleic","ev-b")));
  var result=tools.execute("compareOilMeasurements",Map.of("sampleIds",List.of(a,b),"kind","OIL_FA_COMPOSITION","basis","dry","unit","%"));
  assertEquals("SIDE_BY_SIDE",result.status());assertEquals(2,result.resultCount());
  assertEquals(Set.of(a,b),Set.of(result.records().get(0).fields().get("sampleId"),result.records().get(1).fields().get("sampleId")));
  assertTrue(result.records().stream().allMatch(e->"oleic".equals(e.fields().get("analyte"))&&e.fields().get("value")!=null));
 }
 @Test void comparisonRejectsMixedAnalytesAndFoodNutritionBeforeDatabaseAccess(){
  String a="a".repeat(64),b="b".repeat(64);
  when(science.measurements("FAME_COMPOSITION",a,"dry",null,10)).thenReturn(List.of(row(a,"FAME_COMPOSITION","dry","%","C18:1","ev-a")));
  when(science.measurements("FAME_COMPOSITION",b,"dry",null,10)).thenReturn(List.of(row(b,"FAME_COMPOSITION","dry","%","C18:2","ev-b")));
  var mixed=tools.execute("compareOilMeasurements",Map.of("sampleIds",List.of(a,b),"kind","FAME_COMPOSITION","basis","dry","unit","%"));
  assertEquals("NOT_COMPARABLE",mixed.status());
  assertEquals("NOT_COMPARABLE",tools.execute("compareOilMeasurements",Map.of("sampleIds",List.of(a,b),"kind","OIL_CONTENT","basis","as-reported oil content","unit","%")).status());
  var food=tools.execute("compareOilMeasurements",Map.of("sampleIds",List.of(a,b),"kind","FOOD_NUTRIENT","basis","dry","unit","%"));
  assertEquals("INVALID_ARGUMENTS",food.status());verify(science,never()).measurements("FOOD_NUTRIENT",a,"dry",null,10);
 }
 @Test void sampleEvidenceKeepsNullValuesAndDoesNotInventLinkedSpecies(){
  String id="c".repeat(64);when(science.measurements(null,id,null,null,10)).thenReturn(List.of(new ScientificService.SampleComparison(id,"study","OIL_CONTENT","oleic",null,"%","dry","ev",null,"d".repeat(64))));
  var result=tools.execute("getResearchSamples",Map.of("sampleId",id));
  assertEquals("SUCCESS",result.status());assertNull(result.records().getFirst().fields().get("value"));assertEquals("HUMAN_VERIFIED",result.records().getFirst().knowledgeConfidence());assertEquals("PUBLISHED",result.records().getFirst().publicationState());
  assertFalse(result.records().getFirst().fields().containsKey("scientificName"));
 }
 @Test void machineExtractedSamplesRemainSourceRecords(){
  String id="c".repeat(64);when(science.measurements(null,id,null,null,10)).thenReturn(List.of(new ScientificService.SampleComparison(id,"study","OIL_CONTENT","oil","12","%","dry","ev",new java.math.BigDecimal("12"),"d".repeat(64),"MACHINE_EXTRACTED")));
  var result=tools.execute("getResearchSamples",Map.of("sampleId",id));
  assertEquals("MACHINE_EXTRACTED",result.records().getFirst().knowledgeConfidence());assertEquals("SOURCE_PUBLIC",result.records().getFirst().publicationState());assertEquals("SOURCE_RECORD",result.records().getFirst().category());
 }
 @Test void sampleIdIsOptionalAndEmptyLookupIsInsufficientEvidence(){
  when(science.measurements(null,null,null,null,5)).thenReturn(List.of());
  var result=tools.execute("getResearchSamples",Map.of("limit",5));
  assertEquals("INSUFFICIENT_EVIDENCE",result.status());
 }
 @Test void blankPlantSearchIsAllowedButResultsRemainBoundedAndDeduplicated(){
  when(graph.search("",2,null)).thenReturn(new org.viridexa.api.graph.GraphContract.Page(List.of(),List.of(),null,null,false,2,2,"PUBLIC",""));
  assertEquals("INSUFFICIENT_EVIDENCE",tools.execute("searchPlants",Map.of("query","","limit",2)).status());
  verifyNoInteractions(plants,science);
 }
 @Test void profilePreservesMissingChineseNameAsNullAndLineageUsesSanitizedSourceLocator(){
  when(plants.getById(7)).thenReturn(new org.viridexa.api.plant.PlantRecord(7,null,"Camellia sinensis",null,null,null,null,null,null,null,null,null,null,null));
  var profile=tools.execute("getPlantProfile",Map.of("plantId",7));
  assertEquals("SUCCESS",profile.status());assertNull(profile.records().getFirst().fields().get("nameZh"));
  assertEquals("/api/v1/graph/nodes/plant:7",profile.records().getFirst().href());
  String fact="e".repeat(64);
  when(science.lineage(fact)).thenReturn(List.of(Map.of("id",fact,"sha256","f".repeat(64),"publication_state","PUBLISHED","locator",Map.of("pdf_page",12,"archive_member","table.csv"))));
  var lineage=tools.execute("getEvidenceLineage",Map.of("recordId",fact));
  assertEquals("SUCCESS",lineage.status());assertEquals("/api/v1/science/lineage/"+fact,lineage.records().getFirst().href());
  assertEquals("f".repeat(64),lineage.records().getFirst().fields().get("sha256"));
  assertEquals(12,lineage.records().getFirst().fields().get("locator") instanceof Map<?,?> loc?loc.get("pdf_page"):null);
 }
 @Test void graphNeighborsExposePublishedRelationsAndExcludePendingEdges(){
  String id="plant:9";var published=new org.viridexa.api.graph.GraphContract.Edge("edge:1",id,"taxon:2","IDENTITY_REFERENCE","identified as","PUBLISHED","PUBLISHED",List.of(new org.viridexa.api.graph.GraphContract.SourceReference("source:"+"a".repeat(64),"catalog","a".repeat(64),"v1","OPEN",Map.of())));
  var pending=new org.viridexa.api.graph.GraphContract.Edge("edge:2",id,"taxon:3","IDENTITY_REFERENCE","pending","PENDING","CANDIDATE",List.of());
  when(graph.neighbors(id,null,null,null,5,null,1)).thenReturn(new org.viridexa.api.graph.GraphContract.Page(List.of(),List.of(published,pending),id,null,false,5,40,"PUBLIC",""));
  var result=tools.execute("getGraphNeighbors",Map.of("nodeId",id,"limit",5));
  assertEquals(1,result.resultCount());assertEquals("IDENTITY_REFERENCE",result.records().getFirst().fields().get("relationType"));
  assertEquals(id,result.records().getFirst().graphNodeId());assertEquals("/api/v1/graph/nodes/"+id,result.records().getFirst().href());
  assertEquals("a".repeat(64),result.records().getFirst().sources().getFirst().get("sha256"));
 }
 private static ScientificService.SampleComparison row(String sample,String kind,String basis,String unit,String analyte,String evidence){return new ScientificService.SampleComparison(sample,"study",kind,analyte,"1",unit,basis,evidence,new java.math.BigDecimal("1"),"source:"+"d".repeat(64));}
}
