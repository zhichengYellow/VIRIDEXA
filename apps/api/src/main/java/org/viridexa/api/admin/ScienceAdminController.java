package org.viridexa.api.admin;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

/** Read-only inspection; publication decisions require separately authorized DB identity. */
@RestController
@RequestMapping("/api/v1/admin/science")
public class ScienceAdminController {
    private final JdbcTemplate jdbc;
    public ScienceAdminController(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    @GetMapping("/quality") public List<Map<String,Object>> quality() {
        return jdbc.queryForList("SELECT pipeline,normalization_state,reason,COUNT(*) AS records FROM scientific_candidate GROUP BY pipeline,normalization_state,reason ORDER BY pipeline,normalization_state,reason");
    }
    @GetMapping("/candidates") public Map<String,Object> candidates(
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(required=false) String sourceId,@RequestParam(required=false) String species,
        @RequestParam(required=false) String status,@RequestParam(required=false) String sampleId,
        @RequestParam(required=false) String pipeline) {
        if(page<0||page>100000||size<1||size>100)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"分页超出允许范围");
        var params=new ArrayList<Object>();var where=new StringBuilder(" WHERE 1=1 ");
        if(sourceId!=null&&!sourceId.isBlank()){org.viridexa.api.governance.ReviewService.validateId(sourceId);where.append(" AND c.source_id=?");params.add(sourceId);}
        if(pipeline!=null&&!pipeline.isBlank()){if(!Set.of("TAXONOMY","MORPHOLOGY","LIPID").contains(pipeline))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"未知管线");where.append(" AND c.pipeline=?");params.add(pipeline);}
        if(species!=null&&!species.isBlank()){if(species.length()>200)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"物种检索过长");where.append(" AND (JSON_SEARCH(c.normalized_payload,'all',?) IS NOT NULL OR JSON_SEARCH(c.raw_payload,'all',?) IS NOT NULL)");params.add("%"+species+"%");params.add("%"+species+"%");}
        if(status!=null&&!status.isBlank()){if(!Set.of("PENDING","HELD","APPROVED","REJECTED","NEEDS_EVIDENCE","REVOKED").contains(status))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"未知审核状态");where.append(" AND COALESCE(r.state,IF(c.normalization_state='HELD','HELD','PENDING'))=?");params.add(status);}
        if(sampleId!=null&&!sampleId.isBlank()){if(sampleId.length()>191)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST,"样本标识过长");where.append(" AND EXISTS(SELECT 1 FROM scientific_lipid_observation o JOIN scientific_sample sm ON sm.id=o.sample_id WHERE o.candidate_id=c.id AND (sm.id=? OR sm.sample_key=?))");params.add(sampleId);params.add(sampleId);}
        String from=" FROM scientific_candidate c JOIN scientific_source_version s ON s.id=c.source_id LEFT JOIN scientific_review_state r ON r.candidate_id=c.id ";
        Long total=jdbc.queryForObject("SELECT COUNT(*)"+from+where,Long.class,params.toArray());
        params.add(size);params.add((long)page*size);
        var items=jdbc.queryForList("SELECT c.*,s.dataset,s.path,s.sha256,s.version_label,s.license_state,s.integrity_state,COALESCE(r.version,0) AS version,COALESCE(r.state,IF(c.normalization_state='HELD','HELD','PENDING')) AS review_state"+from+where+" ORDER BY c.id LIMIT ? OFFSET ?",params.toArray());
        Map<String,Object> result=new LinkedHashMap<>();result.put("items",items);result.put("page",page);result.put("size",size);result.put("total",total);return result;
    }
    @GetMapping("/sources") public List<Map<String,Object>> sources() {
        return jdbc.queryForList("SELECT s.*,COALESCE(g.version,0) AS license_version FROM scientific_source_version s LEFT JOIN source_governance g ON g.source_id=s.id ORDER BY s.dataset");
    }

    @GetMapping("/candidates/{id}/lineage") public Map<String,Object> lineage(@PathVariable String id) {
        org.viridexa.api.governance.ReviewService.validateId(id);
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("candidate",jdbc.queryForList("SELECT c.*,s.dataset,s.path,s.sha256,s.version_label,s.license_state,s.integrity_state,COALESCE(r.version,0) AS version,COALESCE(r.state,IF(c.normalization_state='HELD','HELD','PENDING')) AS review_state FROM scientific_candidate c JOIN scientific_source_version s ON s.id=c.source_id LEFT JOIN scientific_review_state r ON r.candidate_id=c.id WHERE c.id=?",id));
        result.put("documents",jdbc.queryForList("SELECT l.locator,l.relation,s.* FROM scientific_evidence_link l JOIN scientific_source_version s ON s.id=l.document_source_id WHERE l.candidate_id=? ORDER BY l.id",id));
        result.put("decisions",jdbc.queryForList("SELECT id,reviewer_id,action,rationale,decided_at FROM scientific_decision WHERE candidate_id=? ORDER BY id",id));
        return result;
    }
    @GetMapping("/sources/{id}/history") public List<Map<String,Object>> sourceHistory(@PathVariable String id) {
        org.viridexa.api.governance.ReviewService.validateId(id);
        return jdbc.queryForList("SELECT id,reviewer_id,license_state,rationale,prior_version,resulting_version,decided_at FROM source_license_event WHERE source_id=? ORDER BY id",id);
    }
}
