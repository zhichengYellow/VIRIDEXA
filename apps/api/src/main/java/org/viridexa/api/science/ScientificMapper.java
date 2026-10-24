package org.viridexa.api.science;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.*;

@Mapper
public interface ScientificMapper {
    @Select("""
        SELECT id, pipeline, normalized_payload, source_id, locator, decision_id
        FROM published_scientific_fact
        WHERE (#{pipeline} IS NULL OR pipeline = #{pipeline})
        ORDER BY id LIMIT #{limit}
        """)
    List<Map<String,Object>> facts(@Param("pipeline") String pipeline, @Param("limit") int limit);

    @Select("""
        SELECT f.id, f.locator, f.raw_payload, f.normalized_payload, f.content_sha256,
               s.dataset, s.version_label, s.path, s.sha256, s.license_state,
               s.integrity_state, d.reviewer_id, d.rationale, d.decided_at
        FROM published_scientific_fact f
        JOIN scientific_source_version s ON s.id=f.source_id
        JOIN scientific_decision d ON d.id=f.decision_id
        WHERE f.id=#{id}
        """)
    List<Map<String,Object>> lineage(@Param("id") String id);

    @Select("""
        SELECT id, pipeline, normalized_payload AS payload, source_id, locator, decision_id
        FROM published_scientific_fact
        WHERE pipeline IN ('TAXONOMY','MORPHOLOGY')
          AND (#{q} IS NULL OR JSON_SEARCH(normalized_payload, 'all', CONCAT('%', #{q}, '%')) IS NOT NULL)
        ORDER BY pipeline, id LIMIT #{limit}
        """)
    List<Map<String,Object>> plantSearch(@Param("q") String q, @Param("limit") int limit);

    @Select("""
        SELECT pipeline, COUNT(*) AS fact_count, COUNT(DISTINCT source_id) AS source_count
        FROM published_scientific_fact GROUP BY pipeline ORDER BY pipeline
        """)
    List<Map<String,Object>> coverage();

    @Select("""
        SELECT s.id AS source_id, s.dataset, s.version_label, s.path AS source_path,
               f.id AS fact_id, f.pipeline, f.locator, f.normalized_payload AS payload,
               f.decision_id
        FROM published_scientific_fact f JOIN scientific_source_version s ON s.id=f.source_id
        WHERE f.id=#{id}
        """)
    List<Map<String,Object>> sourceChain(@Param("id") String id);

    @Select("""
        SELECT o.sample_id, sm.study_key AS study_id, o.kind AS measurement_kind,
               o.analyte, o.raw_value, o.value_numeric, o.unit, o.basis,
               o.candidate_id AS evidence_id, o.source_id
        FROM scientific_lipid_observation o
        JOIN scientific_sample sm ON sm.id=o.sample_id
        JOIN published_scientific_fact f ON f.id=o.candidate_id
        WHERE (#{kind} IS NULL OR o.kind=#{kind})
          AND (#{sampleId} IS NULL OR o.sample_id=#{sampleId})
          AND (#{basis} IS NULL OR o.basis=#{basis})
          AND (#{sourceId} IS NULL OR o.source_id=#{sourceId})
        ORDER BY o.sample_id, o.kind LIMIT #{limit}
        """)
    List<Map<String,Object>> measurements(@Param("kind") String kind, @Param("sampleId") String sampleId,
            @Param("basis") String basis, @Param("sourceId") String sourceId, @Param("limit") int limit);

    @Select("""
        SELECT id,pipeline,normalized_payload,source_id,locator,decision_id
        FROM published_scientific_fact WHERE pipeline='MORPHOLOGY'
        AND (#{leafShape} IS NULL OR JSON_UNQUOTE(JSON_EXTRACT(normalized_payload,'$.traits."leaf shape"')) LIKE CONCAT('%',#{leafShape},'%'))
        AND (#{leafMargin} IS NULL OR JSON_UNQUOTE(JSON_EXTRACT(normalized_payload,'$.traits."leaf margin"')) LIKE CONCAT('%',#{leafMargin},'%'))
        AND (#{flowerColour} IS NULL OR JSON_UNQUOTE(JSON_EXTRACT(normalized_payload,'$.traits."flower colour"')) LIKE CONCAT('%',#{flowerColour},'%'))
        AND (#{growthForm} IS NULL OR JSON_UNQUOTE(JSON_EXTRACT(normalized_payload,'$.traits."habit"')) LIKE CONCAT('%',#{growthForm},'%'))
        AND (#{fruitType} IS NULL OR JSON_UNQUOTE(JSON_EXTRACT(normalized_payload,'$.traits."fruit type"')) LIKE CONCAT('%',#{fruitType},'%'))
        ORDER BY id LIMIT 100
        """)
    List<Map<String,Object>> morphology(@Param("leafShape") String leafShape,@Param("leafMargin") String leafMargin,
        @Param("flowerColour") String flowerColour,@Param("growthForm") String growthForm,@Param("fruitType") String fruitType);

    @Select("""
        SELECT l.candidate_id AS from_id,l.document_source_id AS to_id,l.relation,l.id AS evidence_id
        FROM scientific_evidence_link l JOIN published_scientific_fact f ON f.id=l.candidate_id
        ORDER BY l.id LIMIT 100
        """)
    List<Map<String,Object>> relationships();

    @Select("""
        SELECT l.id AS evidence_id,l.locator,l.relation,s.id AS source_id,s.path,s.sha256,s.version_label,s.license_state,s.integrity_state
        FROM scientific_evidence_link l JOIN scientific_source_version s ON s.id=l.document_source_id
        JOIN published_scientific_fact f ON f.id=l.candidate_id WHERE f.id=#{id}
        ORDER BY l.id
        """)
    List<Map<String,Object>> documents(@Param("id") String id);
}
