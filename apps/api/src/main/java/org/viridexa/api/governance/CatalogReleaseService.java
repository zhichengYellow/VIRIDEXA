package org.viridexa.api.governance;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import jakarta.validation.constraints.*;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Directory decisions reuse the existing review_event ledger; never approve scientific observations. */
@Service
@PreAuthorize("hasRole('ADMIN')")
public class CatalogReleaseService {
 public record Command(@Pattern(regexp="RELEASE|WITHDRAW") @NotNull String action,
   @Min(0) long expectedEventId,@NotBlank @Size(max=500) String expectedScientificName,
   @Pattern(regexp="[0-9a-f]{64}") @NotNull String expectedSourceSha256,
   boolean scientificNameConfirmed,@NotBlank @Size(min=10,max=8000) String rationale,
   @NotNull UUID requestId) {}
 private final ReviewStore store; private final EvidenceIntegrity integrity;
 public CatalogReleaseService(ReviewStore store,EvidenceIntegrity integrity){this.store=store;this.integrity=integrity;}
 public List<Map<String,Object>> list(){return store.jdbc().queryForList("SELECT p.id,p.record_key,p.review_state,p.visibility_state,t.canonical_name,t.identity_state,COALESCE((SELECT MAX(r.id) FROM review_event r WHERE r.entity_type='PLANT' AND r.entity_key=p.record_key),0) AS last_event_id FROM plant p LEFT JOIN taxon t ON t.id=p.taxon_id ORDER BY p.id LIMIT 100");}
 public Map<String,Object> detail(long id){var j=store.jdbc();var rows=j.queryForList("SELECT p.id,p.record_key,p.taxon_id,p.review_state,p.visibility_state,t.canonical_name,t.identity_state,a.sha256,e.locator,COALESCE((SELECT MAX(r.id) FROM review_event r WHERE r.entity_type='PLANT' AND r.entity_key=p.record_key),0) AS last_event_id FROM plant p LEFT JOIN taxon t ON t.id=p.taxon_id LEFT JOIN evidence e ON e.id=t.evidence_id LEFT JOIN source_asset a ON a.id=e.source_asset_id WHERE p.id=?",id);if(rows.isEmpty())throw error(HttpStatus.NOT_FOUND,"目录记录不存在");return Map.of("plant",rows.getFirst(),"sources",j.queryForList("SELECT s.id,s.dataset,s.sha256,s.license_state,s.integrity_state,s.version_label FROM scientific_source_version s JOIN source_asset a ON a.sha256=s.sha256 JOIN evidence e ON e.source_asset_id=a.id JOIN taxon t ON t.evidence_id=e.id JOIN plant p ON p.taxon_id=t.id WHERE p.id=?",id),"history",j.queryForList("SELECT id,action,resulting_state,happened_at,note FROM review_event WHERE entity_type='PLANT' AND entity_key=? ORDER BY id DESC LIMIT 100",rows.getFirst().get("record_key")));}
 public Map<String,Object> decide(long id,Command c,Authentication auth){
  if(c.rationale().trim().length()<10)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"请填写完整的目录决定依据");
  return store.transaction().execute(tx->{var j=store.jdbc();var actors=j.queryForList("SELECT u.id FROM app_user u JOIN user_role ur ON ur.user_id=u.id JOIN app_role r ON r.id=ur.role_id WHERE u.email=? AND u.enabled=TRUE AND r.role_key='ADMIN'",Long.class,auth.getName());if(actors.isEmpty())throw error(HttpStatus.FORBIDDEN,"管理员账户不可用");long actor=actors.getFirst();
   var plants=j.queryForList("SELECT * FROM plant WHERE id=? FOR UPDATE",id);if(plants.isEmpty())throw error(HttpStatus.NOT_FOUND,"目录记录不存在");var plant=plants.getFirst();String key=hash("CATALOG:"+actor+":"+c.requestId()),fingerprint=hash(id+"\n"+c.toString());
   var duplicate=j.queryForList("SELECT id,JSON_UNQUOTE(JSON_EXTRACT(raw_payload,'$.command_hash')) AS command_hash,resulting_state FROM review_event WHERE event_key=? FOR SHARE",key);if(!duplicate.isEmpty()){var old=duplicate.getFirst();if(!fingerprint.equals(old.get("command_hash")))throw error(HttpStatus.CONFLICT,"请求标识已用于其他目录决定");return Map.of("plantId",id,"eventId",old.get("id"),"state",old.get("resulting_state"),"replayed",true);}
   var latest=j.queryForList("SELECT id FROM review_event WHERE entity_type='PLANT' AND entity_key=? ORDER BY id DESC LIMIT 1 FOR SHARE",Long.class,plant.get("record_key"));long version=latest.isEmpty()?0:latest.getFirst();if(version!=c.expectedEventId())throw error(HttpStatus.CONFLICT,"目录已由其他操作更新，请重新核对");
   if(c.action().equals("RELEASE")){
    if(!c.scientificNameConfirmed())throw error(HttpStatus.UNPROCESSABLE_ENTITY,"需由当前管理员实际核对学名后确认");
    var refs=j.queryForList("SELECT t.canonical_name,s.sha256,s.path,s.integrity_state,s.license_state FROM taxon t JOIN evidence e ON e.id=t.evidence_id JOIN source_asset a ON a.id=e.source_asset_id JOIN scientific_source_version s ON s.sha256=a.sha256 WHERE t.id=? FOR SHARE",plant.get("taxon_id"));
    if(refs.size()!=1)throw error(HttpStatus.UNPROCESSABLE_ENTITY,"目录来源缺失或有歧义");var source=refs.getFirst();if(!c.expectedScientificName().equals(source.get("canonical_name"))||!c.expectedSourceSha256().equals(source.get("sha256")))throw error(HttpStatus.CONFLICT,"目录学名或证据版本已变化");
    if(!"VERIFIED".equals(source.get("integrity_state"))||!"PERMITTED".equals(source.get("license_state")))throw error(HttpStatus.UNPROCESSABLE_ENTITY,"来源完整性或公开许可尚未确认");
    integrity.verify((String)source.get("path"),(String)source.get("sha256"));j.update("UPDATE taxon SET identity_state='VERIFIED' WHERE id=?",plant.get("taxon_id"));
   }
   String next=c.action().equals("RELEASE")?"APPROVED":"REVIEW_PENDING";j.update("UPDATE plant SET review_state=?,visibility_state=? WHERE id=?",next,c.action().equals("RELEASE")?"PUBLIC":"RESEARCH_PREVIEW",id);
   j.update("INSERT INTO review_event(event_key,entity_type,entity_key,action,prior_state,resulting_state,actor_user_id,happened_at,note,raw_payload) VALUES(?,'PLANT',?,?,?,?,?,CURRENT_TIMESTAMP(6),?,JSON_OBJECT('command_hash',?,'source_sha256',?,'scientific_name',?,'scientific_observations_approved',FALSE))",key,plant.get("record_key"),"CATALOG_"+c.action(),plant.get("review_state"),next,actor,c.rationale(),fingerprint,c.expectedSourceSha256(),c.expectedScientificName());
   long event=j.queryForObject("SELECT id FROM review_event WHERE event_key=?",Long.class,key);return Map.of("plantId",id,"eventId",event,"state",next,"replayed",false);
  });
 }
 private static String hash(String s){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 private static ResponseStatusException error(HttpStatus status,String text){return new ResponseStatusException(status,text);}
}
