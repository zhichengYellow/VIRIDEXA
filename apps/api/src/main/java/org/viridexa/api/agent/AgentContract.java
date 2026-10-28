package org.viridexa.api.agent;
import java.util.*;
public final class AgentContract {
 private AgentContract(){}
 public record Evidence(String id,String label,String category,String verificationState,String publicationState,Map<String,Object> fields,List<Map<String,Object>> sources,String href,String graphNodeId,String knowledgeConfidence){ public Evidence(String id,String label,String category,String verificationState,String publicationState,Map<String,Object> fields,List<Map<String,Object>> sources,String href,String graphNodeId){this(id,label,category,verificationState,publicationState,fields,sources,href,graphNodeId,"HUMAN_VERIFIED".equals(verificationState)?"HUMAN_VERIFIED":"SOURCE_REPORTED");} }
 public record ToolOutput(String status,String message,int resultCount,List<Evidence> records){}
 public record ToolCall(String id,String name,Map<String,Object> arguments){}
 public record ModelTurn(Map<String,Object> assistantMessage,List<ToolCall> calls,List<String> selectedEvidenceIds,Long inputTokens,Long outputTokens,String actualModel,String actualCost){public ModelTurn(Map<String,Object> assistantMessage,List<ToolCall> calls,List<String> selectedEvidenceIds,Long inputTokens,Long outputTokens){this(assistantMessage,calls,selectedEvidenceIds,inputTokens,outputTokens,null,null);}}
 public record Trace(int sequence,String tool,String status,long elapsedMs,int resultCount,List<String> recordIds,String message,Map<String,Object> arguments){public Trace(int sequence,String tool,String status,long elapsedMs,int resultCount,List<String> recordIds,String message){this(sequence,tool,status,elapsedMs,resultCount,recordIds,message,Map.of());}}
 public record Answer(String status,String summary,List<Evidence> claims,List<String> limitations){}
 public record Usage(int modelCalls,int toolCalls,long inputTokens,long outputTokens,boolean tokenUsageComplete,String currency,String estimatedCost,List<String> actualModels,String actualCost){public Usage(int modelCalls,int toolCalls,long inputTokens,long outputTokens,boolean tokenUsageComplete,String currency,String estimatedCost){this(modelCalls,toolCalls,inputTokens,outputTokens,tokenUsageComplete,currency,estimatedCost,List.of(),null);}}
 public record RunView(String id,String status,String mode,String question,long elapsedMs,List<Trace> trace,Answer answer,Usage usage,String errorCode){}
}
