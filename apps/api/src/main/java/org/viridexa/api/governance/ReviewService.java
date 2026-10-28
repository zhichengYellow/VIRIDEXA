package org.viridexa.api.governance;

import java.util.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.*;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.validation.constraints.*;

@Service
public class ReviewService {
 public record Command(@NotBlank @Pattern(regexp="APPROVED|REJECTED|NEEDS_EVIDENCE|REVOKED") String action,
   @Min(0) long expectedVersion,@NotBlank @Size(min=10,max=8000) String rationale,
   @NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestId) {}
 public record LicenseCommand(@NotBlank @Pattern(regexp="PERMITTED|RESTRICTED|UNKNOWN") String licenseState,
   @Min(0) long expectedVersion,@NotBlank @Size(min=10,max=8000) String rationale,
   @NotBlank @Pattern(regexp="[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}") String requestId) {}
 public record Result(String candidateId,long version,String state,long decisionId,boolean replayed) {}
 private final ReviewStore store;
 private final EvidenceIntegrity integrity;
 public ReviewService(ReviewStore store,EvidenceIntegrity integrity) { this.store=store;this.integrity=integrity; }
 private static String hash(String text) {
  try {return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
  catch(Exception e) {throw new IllegalStateException(e);}
 }
 private long actor(JdbcTemplate jdbc,Authentication auth) {
  var ids=jdbc.queryForList("SELECT u.id FROM app_user u JOIN user_role ur ON ur.user_id=u.id JOIN app_role r ON r.id=ur.role_id WHERE u.email=? AND u.enabled=TRUE AND r.role_key='ADMIN'",Long.class,auth.getName());
  if(ids.isEmpty())throw new ResponseStatusException(HttpStatus.FORBIDDEN,"管理员账户不可用");return ids.getFirst();
 }
 @PreAuthorize("hasRole('ADMIN')")
 public Result decide(String id,Command command,Authentication auth) {
  validateId(id);UUID.fromString(command.requestId());
  try {return store.transaction().execute(status -> {
   var jdbc=store.jdbc();long actor=actor(jdbc,auth);
   String fingerprint=hash(id+"\n"+command.action()+"\n"+command.expectedVersion()+"\n"+command.rationale());
   var duplicate=jdbc.queryForList("SELECT * FROM scientific_decision WHERE reviewer_id=? AND request_id=?",actor,command.requestId());
   if(!duplicate.isEmpty()) {
    var old=duplicate.getFirst();if(!fingerprint.equals(old.get("request_hash")))conflict("请求标识已被其他内容使用");
    return new Result((String)old.get("candidate_id"),((Number)old.get("resulting_version")).longValue(),(String)old.get("action"),((Number)old.get("id")).longValue(),true);
   }
   var candidates=jdbc.queryForList("SELECT c.*,s.integrity_state,s.license_state,s.path AS source_path,s.sha256 AS source_sha256 FROM scientific_candidate c JOIN scientific_source_version s ON s.id=c.source_id WHERE c.id=? FOR SHARE",id);
   if(candidates.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"候选不存在");
   jdbc.update("INSERT INTO scientific_review_state(candidate_id) SELECT ? WHERE NOT EXISTS(SELECT 1 FROM scientific_review_state WHERE candidate_id=?)",id,id);
   var state=jdbc.queryForMap("SELECT * FROM scientific_review_state WHERE candidate_id=? FOR UPDATE",id);
   long version=((Number)state.get("version")).longValue();if(version!=command.expectedVersion())conflict("版本冲突，请刷新证据后重新审核");
   ReviewRules.transition((String)state.get("state"),command.action());
   if(command.action().equals("APPROVED"))validateCandidate(jdbc,candidates.getFirst());
   if(jdbc.update("UPDATE scientific_review_state SET state=?,version=version+1 WHERE candidate_id=? AND version=?",command.action(),id,version)!=1)conflict("并发审核冲突");
   jdbc.update("INSERT INTO scientific_decision(candidate_id,reviewer_id,action,rationale,request_id,request_hash,prior_version,resulting_version) VALUES(?,?,?,?,?,?,?,?)",id,actor,command.action(),command.rationale(),command.requestId(),fingerprint,version,version+1);
   long decision=jdbc.queryForObject("SELECT id FROM scientific_decision WHERE reviewer_id=? AND request_id=?",Long.class,actor,command.requestId());
   return new Result(id,version+1,command.action(),decision,false);
  });} catch (TransientDataAccessException|DuplicateKeyException e) {throw new ResponseStatusException(HttpStatus.CONFLICT,"并发操作，请刷新后重试");}
 }
 private void validateCandidate(JdbcTemplate jdbc,Map<String,Object> row) {
  String id=(String)row.get("id");
  integrity.verify((String)row.get("source_path"),(String)row.get("source_sha256"));
  if(!"CANDIDATE".equals(row.get("normalization_state"))||row.get("normalized_payload")==null)ReviewRules.invalid("隔离或未规范化记录不能批准");
  if(!"VERIFIED".equals(row.get("integrity_state")))ReviewRules.invalid("来源完整性未通过");
  if(!"PERMITTED".equals(row.get("license_state")))ReviewRules.invalid("来源许可需要管理员先完成核验");
  Long bad=jdbc.queryForObject("SELECT COUNT(*) FROM scientific_evidence_link l JOIN scientific_source_version s ON s.id=l.document_source_id WHERE l.candidate_id=? AND (s.integrity_state<>'VERIFIED' OR s.license_state<>'PERMITTED')",Long.class,id);
  if(bad!=0)ReviewRules.invalid("关联原文献的完整性或许可未通过");
  for(var document:jdbc.queryForList("SELECT s.path,s.sha256 FROM scientific_evidence_link l JOIN scientific_source_version s ON s.id=l.document_source_id WHERE l.candidate_id=?",id))integrity.verify((String)document.get("path"),(String)document.get("sha256"));
  var normalized=org.viridexa.api.science.PublicScientificData.object(row.get("normalized_payload"));
  if("TAXONOMY".equals(row.get("pipeline"))) {
   Object name=normalized.getOrDefault("scientific_name",normalized.getOrDefault("scientificName",normalized.get("synonym_name")));
   if(name==null||name.toString().isBlank())ReviewRules.invalid("分类名称缺失，需要补证");
   if(Boolean.TRUE.equals(normalized.get("candidate_ambiguous")))ReviewRules.invalid("分类名称仍有歧义，需要独立身份补证");
  }
  if("MORPHOLOGY".equals(row.get("pipeline"))) {
   if(!normalized.containsKey("scientific_name")&&!normalized.containsKey("identity_text"))ReviewRules.invalid("性状的来源植物身份缺失");
   if(!normalized.containsKey("traits")&&!normalized.containsKey("trait"))ReviewRules.invalid("性状字段缺失");
  }
  if("LIPID".equals(row.get("pipeline"))) {
   var obs=jdbc.queryForList("SELECT o.*,sm.study_key,sm.sample_key FROM scientific_lipid_observation o JOIN scientific_sample sm ON sm.id=o.sample_id WHERE o.candidate_id=? AND o.source_id=?",id,row.get("source_id"));
   if(obs.size()!=1)ReviewRules.invalid("独立样本关系不完整");
   var o=obs.getFirst();if(o.get("study_key")==null||o.get("sample_key")==null)ReviewRules.invalid("研究样本标识缺失");
   if(!Objects.equals(normalized.get("study_id"),o.get("study_key"))||!Objects.equals(normalized.get("sample_id"),o.get("sample_key")))ReviewRules.invalid("规范化结果与独立研究样本不一致");
   for(String field:List.of("kind","unit","basis"))if(!Objects.equals(normalized.get(field),o.get(field)))ReviewRules.invalid("规范化计量定义与观测不一致");
   ReviewRules.lipid((String)o.get("kind"),(String)o.get("unit"),(String)o.get("basis"),(BigDecimal)o.get("value_numeric"));
   try{if(new BigDecimal(String.valueOf(normalized.get("value"))).compareTo((BigDecimal)o.get("value_numeric"))!=0)ReviewRules.invalid("规范化数值与观测不一致");}catch(NumberFormatException e){ReviewRules.invalid("规范化数值缺失或无效");}
  }
  Long located=jdbc.queryForObject("SELECT JSON_LENGTH(locator) FROM scientific_candidate WHERE id=?",Long.class,id);
  if(located==null||located==0)ReviewRules.invalid("原始记录定位缺失");
 }
 @PreAuthorize("hasRole('ADMIN')")
 public Result license(String id,LicenseCommand command,Authentication auth) {
  validateId(id);UUID.fromString(command.requestId());
  try{return store.transaction().execute(status -> {
   var j=store.jdbc();long actor=actor(j,auth);String fp=hash(id+"\n"+command.licenseState()+"\n"+command.expectedVersion()+"\n"+command.rationale());
   var old=j.queryForList("SELECT * FROM source_license_event WHERE reviewer_id=? AND request_id=?",actor,command.requestId());
   if(!old.isEmpty()){var r=old.getFirst();if(!fp.equals(r.get("request_hash")))conflict("请求内容冲突");return new Result(id,((Number)r.get("resulting_version")).longValue(),(String)r.get("license_state"),((Number)r.get("id")).longValue(),true);}
   var source=j.queryForList("SELECT * FROM scientific_source_version WHERE id=?",id);if(source.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"来源不存在");
   if(command.licenseState().equals("PERMITTED")){
    if(!"VERIFIED".equals(source.getFirst().get("integrity_state")))ReviewRules.invalid("完整性未通过，不能授权发布");
    integrity.verify((String)source.getFirst().get("path"),(String)source.getFirst().get("sha256"));
   }
   j.update("INSERT INTO source_governance(source_id) SELECT ? WHERE NOT EXISTS(SELECT 1 FROM source_governance WHERE source_id=?)",id,id);
   if(j.update("UPDATE source_governance SET version=version+1 WHERE source_id=? AND version=?",id,command.expectedVersion())!=1)conflict("来源版本冲突");
   j.update("UPDATE scientific_source_version SET license_state=? WHERE id=?",command.licenseState(),id);
   j.update("INSERT INTO source_license_event(source_id,reviewer_id,license_state,rationale,prior_version,resulting_version,request_id,request_hash) VALUES(?,?,?,?,?,?,?,?)",id,actor,command.licenseState(),command.rationale(),command.expectedVersion(),command.expectedVersion()+1,command.requestId(),fp);
   long event=j.queryForObject("SELECT id FROM source_license_event WHERE reviewer_id=? AND request_id=?",Long.class,actor,command.requestId());
   return new Result(id,command.expectedVersion()+1,command.licenseState(),event,false);
  });}catch(TransientDataAccessException|DuplicateKeyException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"来源并发操作，请刷新");}
 }
 public static void validateId(String id){if(id==null||!id.matches("[0-9a-f]{64}"))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"记录标识格式不正确");}
 private static void conflict(String message){throw new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
