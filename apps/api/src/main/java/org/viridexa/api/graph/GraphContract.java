package org.viridexa.api.graph;

import java.util.*;

/** Live graph projections. Metadata and scientific publication have separate meanings. */
public final class GraphContract {
 private GraphContract() {}
 public record SourceReference(String sourceId,String displayLabel,String sha256,String version,String licenseState,Object locator) {}
 public record Node(String id,String nodeType,String displayLabel,String verificationState,String publicationState,List<SourceReference> sourceReference,Map<String,Object> attributes) {}
 public record Edge(String id,String source,String target,String relationType,String displayLabel,String verificationState,String publicationState,List<SourceReference> sourceReference) {}
 public record Page(List<Node> nodes,List<Edge> edges,String centerId,String nextCursor,boolean hasMore,int nodeLimit,int edgeLimit,String view,String notice) {}
 public record Meta(String view,List<String> nodeTypes,List<String> relationTypes,Map<String,Long> nodeCounts,Map<String,Long> edgeCounts,long publishedFactCount,String notice) {}
}
