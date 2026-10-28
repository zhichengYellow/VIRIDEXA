package org.viridexa.api.workbench;
import java.util.*;
import java.math.BigDecimal;
import org.viridexa.api.graph.GraphContract.SourceReference;
public final class WorkbenchContract {
 private WorkbenchContract(){}
 public record Plant(long id,String scientificName,String nameZh,String identityState,String publicationState){}
 public record Sample(String id,String sampleKey,String studyKey,String label,Long plantId,String taxonId,String identityState,String plantPart,String method,String linkageState,String publicationState,String reviewState,long measurementCount,List<SourceReference> sources){}
 public record Point(String id,String sampleId,Long plantId,String taxonId,String identityState,String plantPart,String kind,String analyte,String rawValue,BigDecimal value,String unit,String basis,String method,String reviewState,String publicationState,String knowledgeConfidence,String sourceId,String sourceLicense,String sourceIntegrity,Map<String,Object> locator,String graphNodeId){public Point(String id,String sampleId,Long plantId,String taxonId,String identityState,String plantPart,String kind,String analyte,String rawValue,BigDecimal value,String unit,String basis,String method,String reviewState,String publicationState,String sourceId,String sourceLicense,String sourceIntegrity,Map<String,Object> locator,String graphNodeId){this(id,sampleId,plantId,taxonId,identityState,plantPart,kind,analyte,rawValue,value,unit,basis,method,reviewState,publicationState,"HUMAN_VERIFIED",sourceId,sourceLicense,sourceIntegrity,locator,graphNodeId);}}
 public record Page<T>(List<T> items,String nextCursor,boolean hasMore,int limit,String scope,String notice){}
 public record Evidence(Point point,List<SourceReference> sources,List<Map<String,Object>> lineage,String availability,String reviewUrl){}
 public record Stat(String key,String label,long count){}
 public record Overview(String scope,long publishedFactCount,long visibleSamples,long unlinkedSamples,List<Stat> states,List<Stat> kinds,List<Stat> sources,String notice){}
 public record CheckItem(String dimension,String status,String reason){}
 public record Converted(String pointId,BigDecimal originalValue,String originalUnit,BigDecimal value,String unit,String rule){}
 public record Comparison(String conclusion,List<CheckItem> checks,List<Converted> values,boolean scientificConclusion,String notice){}
 public record Selection(List<String> pointIds){}
 public record AgentContext(Long plantId,List<String> sampleIds,String sourceId,String kind,String plantPart){}
}
