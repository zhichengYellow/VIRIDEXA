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
    @GetMapping("/candidates") public List<Map<String,Object>> candidates(@RequestParam(defaultValue="30") int limit) {
        return jdbc.queryForList("SELECT * FROM scientific_candidate ORDER BY id LIMIT ?", Math.max(1,Math.min(limit,100)));
    }
    @GetMapping("/sources") public List<Map<String,Object>> sources() {
        return jdbc.queryForList("SELECT * FROM scientific_source_version ORDER BY dataset");
    }

    @GetMapping("/candidates/{id}/lineage") public Map<String,Object> lineage(@PathVariable String id) {
        Map<String,Object> result=new LinkedHashMap<>();
        result.put("candidate",jdbc.queryForList("SELECT c.*,s.dataset,s.path,s.sha256,s.license_state,s.integrity_state FROM scientific_candidate c JOIN scientific_source_version s ON s.id=c.source_id WHERE c.id=?",id));
        result.put("documents",jdbc.queryForList("SELECT l.locator,l.relation,s.* FROM scientific_evidence_link l JOIN scientific_source_version s ON s.id=l.document_source_id WHERE l.candidate_id=? ORDER BY l.id",id));
        result.put("decisions",jdbc.queryForList("SELECT id,reviewer_id,action,rationale,decided_at FROM scientific_decision WHERE candidate_id=? ORDER BY id",id));
        return result;
    }
}
