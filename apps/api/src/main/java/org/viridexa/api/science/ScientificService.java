package org.viridexa.api.science;

import java.util.*;
import org.springframework.stereotype.Service;

/** Read-only contract shared by HTTP and future agent adapters. */
@Service
public class ScientificService {
    private final ScientificMapper mapper;
    public ScientificService(ScientificMapper mapper) { this.mapper = mapper; }
    public List<Map<String,Object>> facts(String pipeline, int limit) {
        if (pipeline != null && !Set.of("TAXONOMY", "MORPHOLOGY", "LIPID").contains(pipeline))
            throw new IllegalArgumentException("Unknown scientific pipeline");
        return mapper.facts(pipeline, Math.max(1, Math.min(limit, 100)));
    }
    public List<Map<String,Object>> lineage(String id) { return mapper.lineage(id); }
    public List<Map<String,Object>> documents(String id) { return mapper.documents(id); }
    public List<Fact> plantSearch(String q, int limit) {
        return mapper.plantSearch(blankToNull(q), bounded(limit)).stream().map(row -> new Fact(
                text(row,"id"), text(row,"pipeline"), row.get("payload"), text(row,"source_id"),
                row.get("locator"), row.get("decision_id"))).toList();
    }
    public List<Coverage> coverage() {
        return mapper.coverage().stream().map(row -> new Coverage(text(row,"pipeline"), number(row,"fact_count"), number(row,"source_count"))).toList();
    }
    public List<Map<String,Object>> sourceChain(String id) { return mapper.sourceChain(id); }
    public List<SampleComparison> measurements(String kind, String sampleId, String basis, String sourceId, int limit) {
        return mapper.measurements(blankToNull(kind), blankToNull(sampleId), blankToNull(basis), blankToNull(sourceId), bounded(limit))
                .stream().map(row -> new SampleComparison(text(row,"sample_id"), text(row,"study_id"),
                        text(row,"measurement_kind"), text(row,"analyte"), text(row,"raw_value"),
                        text(row,"unit"), text(row,"basis"), text(row,"evidence_id"),
                        row.get("value_numeric")==null?null:new java.math.BigDecimal(row.get("value_numeric").toString()),text(row,"source_id"))).toList();
    }
    private static int bounded(int limit) { return Math.max(1, Math.min(limit,100)); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value; }
    private static String text(Map<String,Object> row,String key) { Object v=row.get(key); return v==null?null:v.toString(); }
    private static long number(Map<String,Object> row,String key) { Object v=row.get(key); return v instanceof Number n?n.longValue():Long.parseLong(v.toString()); }
    public record Fact(String id, String pipeline, Object payload, String sourceId, Object locator, Object decisionId) {}
    public record Coverage(String pipeline, long factCount, long sourceCount) {}
    public record GraphNode(String id, String kind, String label, List<String> evidenceIds) {}
    public record GraphEdge(String from, String to, String relationship, List<String> evidenceIds) {}
    public record SampleComparison(String sampleId, String studyId, String measurementKind,
            String analyte, String rawValue, String unit, String basis, String evidenceId, java.math.BigDecimal numericValue, String sourceId) {}

    public List<Map<String,Object>> morphology(String leafShape,String leafMargin,String flowerColour,String growthForm,String fruitType) {
        return mapper.morphology(leafShape,leafMargin,flowerColour,growthForm,fruitType);
    }
    public List<GraphEdge> relationships() {
        return mapper.relationships().stream().map(r -> new GraphEdge((String)r.get("from_id"),(String)r.get("to_id"),
            (String)r.get("relation"),List.of((String)r.get("evidence_id")))).toList();
    }
}
