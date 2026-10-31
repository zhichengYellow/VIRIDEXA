package org.viridexa.api.graph;

import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.viridexa.api.science.PublicScientificData;
import static org.viridexa.api.graph.GraphContract.*;

@Service
@org.springframework.transaction.annotation.Transactional(readOnly=true,timeout=10)
public class GraphService {
 public static final List<String> NODE_TYPES=List.of("PLANT","TAXON","NAME","SOURCE","FILE","SAMPLE","MEASUREMENT","MORPHOLOGY","TAXONOMY","TRAIT","COURSE_SAMPLE","COURSE_MEASUREMENT");
 public static final List<String> RELATION_TYPES=List.of("SOURCE_FILE","IDENTITY_REFERENCE","PARENT_TAXON","HAS_NAME","SOURCED_FROM","HAS_MEASUREMENT","SUPPORTED_BY","HAS_TRAIT","ACCEPTED_TAXON","HAS_RESEARCH_RECORD","SOURCE_NAME_RECORD");
 private static final Set<String> STATES=Set.of("SOURCE_REPORTED","MACHINE_EXTRACTED","VERIFIED_INTEGRITY","HASH_MATCH","PENDING","HELD","APPROVED","REJECTED","NEEDS_EVIDENCE","REVOKED","MISMATCH","INCOMPLETE");
 private static final String PUBLIC=" AND publication_state IN ('METADATA_ONLY','PUBLISHED','SOURCE_PUBLIC') ";
 public static final String NOTICE="目录/名称是来源陈述，哈希匹配仅证明文件对应；SOURCE_PUBLIC 是来源记录（未经具名核验），只有 PUBLISHED 科学节点满足正式发布规则。未知许可不提供原文。";
 private final JdbcTemplate jdbc;
 private final org.viridexa.api.plant.PlantService plants;
 private record MetaSnapshot(String revision,long at,Meta value){}
 private volatile MetaSnapshot publicSnapshot,adminSnapshot;
 private final java.util.concurrent.locks.ReentrantLock metaLock=new java.util.concurrent.locks.ReentrantLock();
 public GraphService(JdbcTemplate jdbc){this(jdbc,null);}
 @org.springframework.beans.factory.annotation.Autowired
 public GraphService(JdbcTemplate jdbc,org.viridexa.api.plant.PlantService plants){this.jdbc=jdbc;this.plants=plants;}
 private static ResponseStatusException bad(String message){return new ResponseStatusException(HttpStatus.BAD_REQUEST,message);}
 public static void validateId(String id){
  if(id==null||!id.matches("(?:(?:source|candidate|sample):[0-9a-f]{64}|(?:plant|taxon|pname|tname|file|course-sample|course-measurement):[1-9][0-9]{0,18}|trait:[0-9a-f]{64}:(?:leaf_shape|leaf_margin|flower_colour|growth_form|fruit_type|fruit_shape):[1-9][0-9]{0,3})"))throw bad("图谱节点标识无效");
  if(id.matches("(?:plant|taxon|pname|tname|file|course-sample|course-measurement):.*"))try{Long.parseLong(id.substring(id.indexOf(':')+1));}catch(NumberFormatException e){throw bad("图谱节点标识超出范围");}
 }
 private static void limit(int value){if(value<1||value>40)throw bad("每页数量必须为1–40");}
 private static String view(boolean admin){return admin?"ADMIN":"PUBLIC";}
 private static String scope(boolean admin){return admin?"":PUBLIC;}
 private static String nodesTable(boolean admin){return admin?"graph_node":"graph_public_node";}
 private static String edgesTable(boolean admin){return admin?"graph_edge":"graph_public_edge";}
 private static String digest(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 static String cursor(String context,String last){return Base64.getUrlEncoder().withoutPadding().encodeToString((digest(context)+"\n"+last).getBytes(StandardCharsets.UTF_8));}
 static String after(String cursor,String context){
  if(cursor==null||cursor.isBlank())return "";
  try{if(cursor.length()>900)throw bad("游标过长");var parts=new String(Base64.getUrlDecoder().decode(cursor),StandardCharsets.UTF_8).split("\n",-1);if(parts.length!=2||!parts[0].equals(digest(context))||parts[1].length()>300)throw bad("游标不属于当前查询");return parts[1];}
  catch(IllegalArgumentException e){throw bad("游标格式无效");}
 }
 public Page search(String q,int size,String cursor){return search(false,q,size,cursor);}
 @PreAuthorize("hasRole('ADMIN')") public Page adminSearch(String q,int size,String cursor){return search(true,q,size,cursor);}
 private Page search(boolean admin,String q,int size,String cursor){
  limit(size);q=q==null?"":q.trim();if(q.length()>160)throw bad("名称检索过长");
  String context=view(admin)+"|search|"+q+"|"+size;String last=after(cursor,context);
  if(!admin&&!q.isEmpty()&&plants!=null)return publicEntrances(q,size,context,last);
  String where=q.isEmpty()?" AND node_type='SOURCE'":" AND node_type<>'TRAIT' AND (display_label LIKE ? ESCAPE '!' OR id=?)";
  var args=new ArrayList<Object>();args.add(last);if(!q.isEmpty()){args.add("%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%");args.add(q);}args.add(size+1);
  var rows=jdbc.queryForList("SELECT * FROM "+nodesTable(admin)+" WHERE id>?"+scope(admin)+where+" ORDER BY id LIMIT ?",args.toArray());
  boolean more=rows.size()>size;var nodes=rows.stream().limit(size).map(this::node).toList();
  return new Page(nodes,List.of(),null,more?cursor(context,nodes.getLast().id()):null,more,40,0,view(admin),NOTICE);
 }
 private Page publicEntrances(String q,int size,String context,String last){
  var result=new ArrayList<Node>();
  // The graph starts from the existing public catalog service, then expands observations.
  // This avoids a LIKE scan over all graph projections and retains catalog permissions.
  if(!last.startsWith("source:")){
   long afterId=last.startsWith("plant:")?Long.parseLong(last.substring(6)):0;
   for(var p:plants.searchAfter(q,size+1,afterId).items()){
    var attrs=new LinkedHashMap<String,Object>();if(p.nameZh()!=null)attrs.put("nameZh",p.nameZh());if(p.scientificName()!=null)attrs.put("scientificName",p.scientificName());
    var refs=new ArrayList<SourceReference>();
    if(p.sourcePdfSha256()!=null)for(var row:jdbc.queryForList("SELECT id AS source_id FROM scientific_source_version WHERE sha256=? ORDER BY id LIMIT 1",p.sourcePdfSha256()))refs.addAll(references(row));
    result.add(new Node("plant:"+p.id(),"PLANT",p.scientificName(),"SOURCE_REPORTED","METADATA_ONLY",refs,attrs));
   }
  }
  if(result.size()<=size){
   String pattern="%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
   for(var row:jdbc.queryForList("SELECT id FROM scientific_source_version WHERE CONCAT('source:',id)>? AND (dataset LIKE ? ESCAPE '!' OR id=?) ORDER BY id LIMIT ?",last,pattern,q,size+1-result.size()))result.add(detail(false,"source:"+row.get("id")));
  }
  boolean more=result.size()>size;var visible=result.stream().limit(size).toList();
  return new Page(visible,List.of(),null,more?cursor(context,visible.getLast().id()):null,more,40,0,"PUBLIC",NOTICE+" 名称检索从公开目录与来源入口开始，观测与性状通过真实关系展开。");
 }
 public Node detail(String id){return detail(false,id);}
 @PreAuthorize("hasRole('ADMIN')") public Node adminDetail(String id){return detail(true,id);}
 private Node detail(boolean admin,String id){
  validateId(id);String prefix=id.substring(0,id.indexOf(':')),key=id.substring(id.indexOf(':')+1);
  List<Map<String,Object>> rows;
  if(prefix.equals("candidate")){
   rows=jdbc.queryForList("SELECT CONCAT('candidate:',c.id) AS id,IF(c.pipeline='LIPID','MEASUREMENT',c.pipeline) AS node_type,COALESCE(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.scientific_name')),JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.scientificName')),JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.synonym_name')),CONCAT(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.sample_id')),' / ',COALESCE(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.analyte')),JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.kind')))),CONCAT(c.pipeline,' / ',LEFT(c.id,12))) AS display_label,c.graph_state AS verification_state,c.publication_state,c.source_id,c.locator,JSON_OBJECT('pipeline',c.pipeline,'normalizationState',c.normalization_state) AS attributes FROM graph_candidate c WHERE c.id=?"+scope(admin),key);
  }else if(prefix.equals("trait")){
   var parts=key.split(":");rows=jdbc.queryForList("SELECT CONCAT('trait:',candidate_id,':',field_key,':',ordinality) AS id,'TRAIT' AS node_type,CONCAT(field_key,': ',source_term) AS display_label,graph_state AS verification_state,publication_state,source_id,locator,JSON_OBJECT('field',field_key,'sourceTerm',source_term,'concept',concept,'vocabularyVersion',vocabulary_version) AS attributes FROM graph_trait WHERE candidate_id=? AND field_key=? AND ordinality=?"+scope(admin),parts[0],parts[1],Integer.parseInt(parts[2]));
  }else if(prefix.equals("pname")||prefix.equals("tname")){
   boolean plantName=prefix.equals("pname");String table=plantName?"plant_name":"taxon_name",owner=plantName?"plant_id":"taxon_id";
   var names=jdbc.queryForList("SELECT n.*,e.locator,s.id AS source_id FROM "+table+" n LEFT JOIN evidence e ON e.id=n.evidence_id LEFT JOIN source_asset a ON a.id=e.source_asset_id LEFT JOIN scientific_source_version s ON s.sha256=a.sha256 WHERE n.id=? LIMIT 1",Long.parseLong(key));
   if(names.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"名称记录不存在");var row=names.getFirst();
   Node parent=detail(admin,(plantName?"plant:":"taxon:")+row.get(owner));
   boolean visible=Set.of("APPROVED","VERIFIED_FROM_PRINTED_HEADING","SOURCE_REPORTED").contains(text(row,"review_state"));
   if(!admin&&!visible)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"名称未公开");
   var attrs=new LinkedHashMap<String,Object>();attrs.put("role",row.get("name_role"));attrs.put("language",row.get("language_tag"));attrs.put("reviewState",row.get("review_state"));
   return new Node(id,"NAME",text(row,"name_text"),"SOURCE_REPORTED",visible&&"METADATA_ONLY".equals(parent.publicationState())?"METADATA_ONLY":"CANDIDATE",references(row),attrs);
  }else if(prefix.equals("plant")){
   rows=jdbc.queryForList("SELECT CONCAT('plant:',p.id) AS id,'PLANT' AS node_type,COALESCE(t.canonical_name,CONCAT('Plant ',p.id)) AS display_label,'SOURCE_REPORTED' AS verification_state,IF(p.is_public=1,'METADATA_ONLY','CANDIDATE') AS publication_state,p.source_id,p.locator,JSON_OBJECT('reviewState',p.review_state,'visibilityState',p.visibility_state) AS attributes FROM graph_catalog p LEFT JOIN taxon t ON t.id=p.taxon_id WHERE p.id=?"+(admin?"":" AND p.is_public=1"),Long.parseLong(key));
  }else if(prefix.equals("taxon")){
   rows=jdbc.queryForList("SELECT CONCAT('taxon:',t.id) AS id,'TAXON' AS node_type,t.canonical_name AS display_label,'SOURCE_REPORTED' AS verification_state,IF(t.is_public=1,'METADATA_ONLY','CANDIDATE') AS publication_state,t.source_id,t.locator,JSON_OBJECT('rank',t.taxon_rank,'identityState',t.identity_state) AS attributes FROM graph_taxon t WHERE t.id=?"+(admin?"":" AND t.is_public=1"),Long.parseLong(key));
  }else if(prefix.equals("sample")){
   String visible="EXISTS(SELECT 1 FROM scientific_lipid_observation o JOIN public_source_record f ON f.id=o.candidate_id WHERE o.sample_id=sm.id)";
   rows=jdbc.queryForList("SELECT CONCAT('sample:',sm.id) AS id,'SAMPLE' AS node_type,CONCAT(sm.study_key,' / ',sm.sample_key) AS display_label,'SOURCE_REPORTED' AS verification_state,IF("+visible+",'SOURCE_PUBLIC','CANDIDATE') AS publication_state,sm.source_id,JSON_OBJECT() AS locator,JSON_OBJECT('studyId',sm.study_key,'sampleKey',sm.sample_key,'linkageState',sm.linkage_state) AS attributes FROM scientific_sample sm WHERE sm.id=?"+(admin?"":" AND "+visible),key);
  }else{
   String type=switch(prefix){case "source"->"SOURCE";case "file"->"FILE";case "plant"->"PLANT";case "taxon"->"TAXON";case "pname","tname"->"NAME";case "course-sample"->"COURSE_SAMPLE";case "course-measurement"->"COURSE_MEASUREMENT";default->"SAMPLE";};
   rows=jdbc.queryForList("SELECT * FROM "+nodesTable(admin)+" WHERE id=? AND node_type=?"+scope(admin),id,type);
  }
  if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND,"节点不存在或未获公开授权");
  Node n=node(rows.getFirst());
  if(id.startsWith("candidate:")){
   var attrs=new LinkedHashMap<>(n.attributes());
   if(admin){var row=jdbc.queryForMap("SELECT raw_payload,normalized_payload,reason FROM scientific_candidate WHERE id=?",id.substring(10));attrs.put("rawValue",PublicScientificData.object(row.get("raw_payload")));attrs.put("normalizedValue",PublicScientificData.object(row.get("normalized_payload")));attrs.put("reason",row.get("reason"));attrs.put("reviewUrl","/review?candidateId="+id.substring(10));}
   else attrs.put("normalizedValue",PublicScientificData.payload(jdbc.queryForObject("SELECT normalized_payload FROM public_source_record WHERE id=?",String.class,id.substring(10))));
   var obs=jdbc.queryForList("SELECT o.sample_id,sm.study_key,sm.sample_key,sm.linkage_state,o.kind,o.value_numeric,o.unit,o.basis,o.analyte FROM scientific_lipid_observation o JOIN scientific_sample sm ON sm.id=o.sample_id WHERE o.candidate_id=?",id.substring(10));
   if(!obs.isEmpty())attrs.put("sample",obs.getFirst());
   n=new Node(n.id(),n.nodeType(),n.displayLabel(),n.verificationState(),n.publicationState(),n.sourceReference(),attrs);
  }
  return n;
 }
 @org.springframework.transaction.annotation.Transactional(readOnly=true,timeout=20)
 public Page neighbors(String id,String relation,String type,String state,int size,String cursor,int depth){return neighbors(false,id,relation,type,state,null,size,cursor,depth);}
 @PreAuthorize("hasRole('ADMIN')") public Page adminNeighbors(String id,String relation,String type,String state,int size,String cursor,int depth){return neighbors(true,id,relation,type,state,null,size,cursor,depth);}
 @org.springframework.transaction.annotation.Transactional(readOnly=true,timeout=20)
 public Page neighbors(String id,String relation,String type,String state,String publication,int size,String cursor,int depth){return neighbors(false,id,relation,type,state,publication,size,cursor,depth);}
 @PreAuthorize("hasRole('ADMIN')") public Page adminNeighbors(String id,String relation,String type,String state,String publication,int size,String cursor,int depth){return neighbors(true,id,relation,type,state,publication,size,cursor,depth);}
 // Push native-key predicates into each relationship branch before UNION. Public endpoint
 // checks and the exact graph_public_edge definitions are preserved; no graph data is copied.
 private record EdgeQuery(String sql,List<Object> parameters,String sortPrefix){}
 private String publicEdges(String prefix,String key,List<Object> args,String last,String relation,String type,String state,String publication,int size,List<EdgeQuery> sourceQueries){
  args.clear();var branches=new ArrayList<String>();
  switch(prefix){
   case "candidate" -> {
    branches.add("""
SELECT CONCAT('cs:',c.id),CONCAT('candidate:',c.id),CONCAT('source:',c.source_id),'SOURCED_FROM','观测直接来源',c.graph_state,c.publication_state,c.source_id,c.locator FROM graph_candidate c WHERE c.id IN(SELECT id FROM public_source_record) AND c.id=?
""");
    args.add(key);
    branches.add("""
SELECT CONCAT('sm:',o.candidate_id),CONCAT('sample:',o.sample_id),CONCAT('candidate:',o.candidate_id),'HAS_MEASUREMENT','独立样本观测',c.graph_state,c.publication_state,c.source_id,c.locator FROM scientific_lipid_observation o JOIN graph_candidate c ON c.id=o.candidate_id AND c.source_id=o.source_id JOIN scientific_sample sm ON sm.id=o.sample_id WHERE JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.study_id'))=sm.study_key AND JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.sample_id'))=sm.sample_key AND c.id IN(SELECT id FROM public_source_record) AND o.candidate_id=?
""");
    args.add(key);
    branches.add("""
SELECT CONCAT('doc:',l.id),CONCAT('candidate:',l.candidate_id),CONCAT('source:',l.document_source_id),'SUPPORTED_BY','明确关联原文献',c.graph_state,c.publication_state,l.document_source_id,l.locator FROM scientific_evidence_link l JOIN graph_candidate c ON c.id=l.candidate_id WHERE c.id IN(SELECT id FROM public_source_record) AND l.candidate_id=?
""");
    args.add(key);
    branches.add("""
SELECT CONCAT('accepted_taxon_id:',c.id,':',t.id),CONCAT('candidate:',c.id),CONCAT('candidate:',t.id),'ACCEPTED_TAXON','同来源版本原生分类 ID 引用',c.graph_state,IF(c.publication_state IN ('PUBLISHED','SOURCE_PUBLIC') AND IF(EXISTS(SELECT 1 FROM public_source_record target_fact WHERE target_fact.id=t.id),'SOURCE_PUBLIC','CANDIDATE') IN ('PUBLISHED','SOURCE_PUBLIC'),'SOURCE_PUBLIC','CANDIDATE'),c.source_id,c.locator FROM graph_candidate c JOIN scientific_candidate t FORCE INDEX (ix_graph_taxon_reference) ON t.source_id=c.source_id AND t.pipeline='TAXONOMY' AND t.graph_namespace=LEFT(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace')),32) AND t.graph_native_taxon_id=LEFT(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.accepted_taxon_id')),191) AND (CAST(JSON_UNQUOTE(JSON_EXTRACT(t.normalized_payload,'$.source_taxon_id')) AS CHAR(191)) COLLATE utf8mb4_bin)=(CAST(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.accepted_taxon_id')) AS CHAR(191)) COLLATE utf8mb4_bin) AND (CAST(JSON_UNQUOTE(JSON_EXTRACT(t.normalized_payload,'$.source_namespace')) AS CHAR(32)) COLLATE utf8mb4_bin)=(CAST(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace')) AS CHAR(32)) COLLATE utf8mb4_bin) WHERE c.pipeline='TAXONOMY' AND JSON_TYPE(JSON_EXTRACT(c.normalized_payload,'$.accepted_taxon_id'))='STRING' AND JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.accepted_taxon_id'))<>'' AND JSON_TYPE(JSON_EXTRACT(t.normalized_payload,'$.source_taxon_id'))='STRING' AND JSON_TYPE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace'))='STRING' AND c.id IN(SELECT id FROM public_source_record) AND t.id IN(SELECT id FROM public_source_record) AND (c.id=? OR t.id=?)
""");
    args.add(key);args.add(key);
    branches.add("""
SELECT CONCAT('parent_taxon_id:',c.id,':',t.id),CONCAT('candidate:',c.id),CONCAT('candidate:',t.id),'PARENT_TAXON','同来源版本原生分类 ID 引用',c.graph_state,IF(c.publication_state IN ('PUBLISHED','SOURCE_PUBLIC') AND IF(EXISTS(SELECT 1 FROM public_source_record target_fact WHERE target_fact.id=t.id),'SOURCE_PUBLIC','CANDIDATE') IN ('PUBLISHED','SOURCE_PUBLIC'),'SOURCE_PUBLIC','CANDIDATE'),c.source_id,c.locator FROM graph_candidate c JOIN scientific_candidate t FORCE INDEX (ix_graph_taxon_reference) ON t.source_id=c.source_id AND t.pipeline='TAXONOMY' AND t.graph_namespace=LEFT(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace')),32) AND t.graph_native_taxon_id=LEFT(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.parent_taxon_id')),191) AND (CAST(JSON_UNQUOTE(JSON_EXTRACT(t.normalized_payload,'$.source_taxon_id')) AS CHAR(191)) COLLATE utf8mb4_bin)=(CAST(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.parent_taxon_id')) AS CHAR(191)) COLLATE utf8mb4_bin) AND (CAST(JSON_UNQUOTE(JSON_EXTRACT(t.normalized_payload,'$.source_namespace')) AS CHAR(32)) COLLATE utf8mb4_bin)=(CAST(JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace')) AS CHAR(32)) COLLATE utf8mb4_bin) WHERE c.pipeline='TAXONOMY' AND JSON_TYPE(JSON_EXTRACT(c.normalized_payload,'$.parent_taxon_id'))='STRING' AND JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.parent_taxon_id'))<>'' AND JSON_TYPE(JSON_EXTRACT(t.normalized_payload,'$.source_taxon_id'))='STRING' AND JSON_TYPE(JSON_EXTRACT(c.normalized_payload,'$.source_namespace'))='STRING' AND c.id IN(SELECT id FROM public_source_record) AND t.id IN(SELECT id FROM public_source_record) AND (c.id=? OR t.id=?)
""");
    args.add(key);args.add(key);
    branches.add("""
SELECT CONCAT('source-name:',p.id,':',f.id),CONCAT('plant:',p.id),CONCAT('candidate:',f.id),'SOURCE_NAME_RECORD','原文学名精确相同的来源记录',f.knowledge_confidence,'SOURCE_PUBLIC',f.source_id,f.locator
FROM graph_catalog p JOIN taxon t ON t.id=p.taxon_id JOIN scientific_candidate c ON c.graph_scientific_name=LEFT(t.canonical_name,191) COLLATE utf8mb4_bin
 JOIN public_source_record f ON f.id=c.id
WHERE p.is_public=1 AND BINARY JSON_UNQUOTE(JSON_EXTRACT(f.normalized_payload,'$.scientific_name'))=BINARY t.canonical_name AND f.id=?
""");
    args.add(key);
   }
   case "source" -> {
    branches.add("""
SELECT CONCAT('sf:',s.id,':',a.id) AS id,CONCAT('source:',s.id) AS from_id,CONCAT('file:',a.id) AS to_id,'SOURCE_FILE' AS relation_type,'SHA-256 相同的注册文件' AS display_label,'HASH_MATCH' AS verification_state,'METADATA_ONLY' AS publication_state,s.id AS source_id,JSON_OBJECT('match','EXACT_SHA256') AS locator FROM scientific_source_version s JOIN source_asset a ON a.sha256=s.sha256 WHERE s.id=?
""");
    args.add(key);
    branches.add("""
SELECT CONCAT('ts:',t.id),CONCAT('taxon:',t.id),CONCAT('source:',t.source_id),'SOURCED_FROM','分类原始证据','SOURCE_REPORTED',IF(t.is_public=1,'METADATA_ONLY','CANDIDATE'),t.source_id,t.locator FROM graph_taxon t WHERE t.source_id IS NOT NULL AND t.is_public=1 AND t.source_id=?
""");
    args.add(key);
    branches.add("""
SELECT CONCAT('cs:',c.id),CONCAT('candidate:',c.id),CONCAT('source:',c.source_id),'SOURCED_FROM','观测直接来源',c.graph_state,c.publication_state,c.source_id,c.locator FROM graph_candidate c WHERE c.id IN(SELECT id FROM public_source_record) AND c.source_id=?
""");
    args.add(key);
    branches.add("""
SELECT CONCAT('ss:',s.id),CONCAT('sample:',s.id),CONCAT('source:',s.source_id),'SOURCED_FROM','样本登记来源','SOURCE_REPORTED',IF(EXISTS(SELECT 1 FROM scientific_lipid_observation o JOIN public_source_record f ON f.id=o.candidate_id WHERE o.sample_id=s.id),'SOURCE_PUBLIC','CANDIDATE'),s.source_id,JSON_OBJECT('study_id',s.study_key,'sample_id',s.sample_key) FROM scientific_sample s WHERE s.id IN(SELECT o.sample_id FROM scientific_lipid_observation o JOIN public_source_record f ON f.id=o.candidate_id) AND s.source_id=?
""");
    args.add(key);
    branches.add("""
SELECT CONCAT('doc:',l.id),CONCAT('candidate:',l.candidate_id),CONCAT('source:',l.document_source_id),'SUPPORTED_BY','明确关联原文献',c.graph_state,c.publication_state,l.document_source_id,l.locator FROM scientific_evidence_link l JOIN graph_candidate c ON c.id=l.candidate_id WHERE c.id IN(SELECT id FROM public_source_record) AND l.document_source_id=?
""");
    args.add(key);
   }
   case "file" -> {
    branches.add("""
SELECT CONCAT('sf:',s.id,':',a.id) AS id,CONCAT('source:',s.id) AS from_id,CONCAT('file:',a.id) AS to_id,'SOURCE_FILE' AS relation_type,'SHA-256 相同的注册文件' AS display_label,'HASH_MATCH' AS verification_state,'METADATA_ONLY' AS publication_state,s.id AS source_id,JSON_OBJECT('match','EXACT_SHA256') AS locator FROM scientific_source_version s JOIN source_asset a ON a.sha256=s.sha256 WHERE a.id=?
""");
    args.add(key);
   }
   case "taxon" -> {
    branches.add("""
SELECT CONCAT('pi:',p.id),CONCAT('plant:',p.id),CONCAT('taxon:',p.taxon_id),'IDENTITY_REFERENCE','目录分类身份引用','SOURCE_REPORTED',IF(p.is_public=1,'METADATA_ONLY','CANDIDATE'),p.source_id,p.locator FROM graph_catalog p WHERE p.taxon_id IS NOT NULL AND p.is_public=1 AND p.taxon_id=?
""");
    args.add(key);
    branches.add("""
SELECT CONCAT('tp:',t.id),CONCAT('taxon:',t.id),CONCAT('taxon:',t.parent_id),'PARENT_TAXON','来源分类父级','SOURCE_REPORTED',IF(t.is_public=1 AND parent.is_public=1,'METADATA_ONLY','CANDIDATE'),t.source_id,t.locator FROM graph_taxon t JOIN graph_taxon parent ON parent.id=t.parent_id WHERE t.is_public=1 AND parent.is_public=1 AND (t.id=? OR t.parent_id=?)
""");
    args.add(key);args.add(key);
    branches.add("""
SELECT CONCAT('tn:',n.id),CONCAT('taxon:',n.taxon_id),CONCAT('tname:',n.id),'HAS_NAME','分类单元名称','SOURCE_REPORTED',IF(t.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED'),'METADATA_ONLY','CANDIDATE'),t.source_id,e.locator FROM taxon_name n JOIN graph_taxon t ON t.id=n.taxon_id LEFT JOIN evidence e ON e.id=n.evidence_id WHERE t.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED') AND n.taxon_id=?
""");
    args.add(key);
    branches.add("""
SELECT CONCAT('ts:',t.id),CONCAT('taxon:',t.id),CONCAT('source:',t.source_id),'SOURCED_FROM','分类原始证据','SOURCE_REPORTED',IF(t.is_public=1,'METADATA_ONLY','CANDIDATE'),t.source_id,t.locator FROM graph_taxon t WHERE t.source_id IS NOT NULL AND t.is_public=1 AND t.id=?
""");
    args.add(key);
   }
   case "pname" -> {
    branches.add("""
SELECT CONCAT('pn:',n.id),CONCAT('plant:',n.plant_id),CONCAT('pname:',n.id),'HAS_NAME','目录记录名称','SOURCE_REPORTED',IF(p.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED'),'METADATA_ONLY','CANDIDATE'),p.source_id,e.locator FROM plant_name n JOIN graph_catalog p ON p.id=n.plant_id LEFT JOIN evidence e ON e.id=n.evidence_id WHERE p.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED') AND n.id=?
""");
    args.add(key);
   }
   case "tname" -> {
    branches.add("""
SELECT CONCAT('tn:',n.id),CONCAT('taxon:',n.taxon_id),CONCAT('tname:',n.id),'HAS_NAME','分类单元名称','SOURCE_REPORTED',IF(t.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED'),'METADATA_ONLY','CANDIDATE'),t.source_id,e.locator FROM taxon_name n JOIN graph_taxon t ON t.id=n.taxon_id LEFT JOIN evidence e ON e.id=n.evidence_id WHERE t.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED') AND n.id=?
""");
    args.add(key);
   }
   case "sample" -> {
    branches.add("""
SELECT CONCAT('sm:',o.candidate_id),CONCAT('sample:',o.sample_id),CONCAT('candidate:',o.candidate_id),'HAS_MEASUREMENT','独立样本观测',c.graph_state,c.publication_state,c.source_id,c.locator FROM scientific_lipid_observation o JOIN graph_candidate c ON c.id=o.candidate_id AND c.source_id=o.source_id JOIN scientific_sample sm ON sm.id=o.sample_id WHERE JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.study_id'))=sm.study_key AND JSON_UNQUOTE(JSON_EXTRACT(c.normalized_payload,'$.sample_id'))=sm.sample_key AND c.id IN(SELECT id FROM public_source_record) AND o.sample_id=?
""");
    args.add(key);
    branches.add("""
SELECT CONCAT('ss:',s.id),CONCAT('sample:',s.id),CONCAT('source:',s.source_id),'SOURCED_FROM','样本登记来源','SOURCE_REPORTED',IF(EXISTS(SELECT 1 FROM scientific_lipid_observation o JOIN public_source_record f ON f.id=o.candidate_id WHERE o.sample_id=s.id),'SOURCE_PUBLIC','CANDIDATE'),s.source_id,JSON_OBJECT('study_id',s.study_key,'sample_id',s.sample_key) FROM scientific_sample s WHERE s.id IN(SELECT o.sample_id FROM scientific_lipid_observation o JOIN public_source_record f ON f.id=o.candidate_id) AND s.id=?
""");
    args.add(key);
   }
   default -> { }
  }
  if(prefix.equals("source")){
   args.clear();var bounded=new ArrayList<String>();
   for(String branch:branches){
    int argumentStart=args.size();
    for(long n=branch.chars().filter(c->c=='?').count();n>0;n--)args.add(key);
    String filter=" WHERE id>?"+PUBLIC;args.add(last);
    if(relation!=null){filter+=" AND relation_type=?";args.add(relation);}
    if(state!=null){filter+=" AND verification_state=?";args.add(state);}
    if(publication!=null){filter+=" AND publication_state=?";args.add(publication);}
    if(type!=null){
     String neighbor="IF(from_id=CONCAT('source:',?),to_id,from_id)";args.add(key);
     if(Set.of("MEASUREMENT","MORPHOLOGY","TAXONOMY").contains(type)){
      filter+=" AND EXISTS(SELECT 1 FROM scientific_candidate nt WHERE nt.id=SUBSTRING("+neighbor+",11) AND nt.pipeline=?)";args.add(type.equals("MEASUREMENT")?"LIPID":type);
     }else if(type.equals("NAME")){filter+=" AND ("+neighbor+" LIKE 'pname:%' OR "+neighbor+" LIKE 'tname:%')";args.add(key);}
     else{filter+=" AND "+neighbor+" LIKE ?";args.add(type.toLowerCase(Locale.ROOT).replace('_','-')+":%");}
    }
    args.add(size+1);String projection=String.join(",",List.of("id","from_id","to_id","relation_type","display_label","verification_state","publication_state","source_id","locator").stream().map(k->"CAST("+k+" AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_bin AS "+k).toList());String query="SELECT * FROM (SELECT "+projection+" FROM ("+branch+") ge(id,from_id,to_id,relation_type,display_label,verification_state,publication_state,source_id,locator)"+filter+" ORDER BY id LIMIT ?) bounded_branch";bounded.add(query);String prefixKey=branch.startsWith("SELECT CONCAT('cs:'")?"cs:":branch.startsWith("SELECT CONCAT('doc:'")?"doc:":branch.startsWith("SELECT CONCAT('sf:'")?"sf:":branch.startsWith("SELECT CONCAT('ss:'")?"ss:":"ts:";sourceQueries.add(new EdgeQuery(query,new ArrayList<>(args.subList(argumentStart,args.size())),prefixKey));
   }
   branches=bounded;
  }
  return "SELECT id,from_id,to_id,relation_type,display_label,verification_state,publication_state,source_id,locator FROM graph_trait_edge WHERE 1=0"+(branches.isEmpty()?"":" UNION ALL "+String.join(" UNION ALL ",branches));
 }
 private Page neighbors(boolean admin,String id,String relation,String type,String state,String publication,int size,String cursor,int depth){
  limit(size);if(depth!=1)throw bad("仅支持一跳展开");
  if(relation!=null&&!RELATION_TYPES.contains(relation))throw bad("未知关系类型");if(type!=null&&!NODE_TYPES.contains(type))throw bad("未知节点类型");if(state!=null&&!STATES.contains(state))throw bad("未知核验状态");
  if(publication!=null&&!Set.of("METADATA_ONLY","PUBLISHED","SOURCE_PUBLIC","CANDIDATE").contains(publication))throw bad("未知发布状态");
  Node center=detail(admin,id);String context=view(admin)+"|neighbors|"+id+"|"+relation+"|"+type+"|"+state+"|"+publication+"|"+size;String last=after(cursor,context);
  var args=new ArrayList<Object>();var sourceQueries=new ArrayList<EdgeQuery>();String where="";
  String prefix=id.substring(0,id.indexOf(':'));
  var possible=switch(prefix){case "source"->List.of("SOURCE_FILE","SOURCED_FROM","SUPPORTED_BY");case "file"->List.of("SOURCE_FILE","SOURCED_FROM");case "plant"->List.of("IDENTITY_REFERENCE","HAS_NAME","HAS_RESEARCH_RECORD","SOURCE_NAME_RECORD");case "taxon"->List.of("IDENTITY_REFERENCE","PARENT_TAXON","HAS_NAME","SOURCED_FROM");case "pname","tname"->List.of("HAS_NAME");case "sample"->List.of("HAS_MEASUREMENT","SOURCED_FROM");case "course-sample"->List.of("HAS_RESEARCH_RECORD","HAS_MEASUREMENT","SOURCED_FROM");case "course-measurement"->List.of("HAS_MEASUREMENT","SOURCED_FROM");case "trait"->List.of("HAS_TRAIT");default->List.of("SOURCED_FROM","HAS_MEASUREMENT","SUPPORTED_BY","HAS_TRAIT","ACCEPTED_TAXON","PARENT_TAXON","SOURCE_NAME_RECORD");};
  if(!admin&&prefix.equals("source")&&metadataOnly())possible=List.of("SOURCE_FILE");
  String fields="id,from_id,to_id,relation_type,display_label,verification_state,publication_state,source_id,locator";
  String base="SELECT "+fields+" FROM "+edgesTable(admin)+" WHERE relation_type<>'HAS_TRAIT' AND (from_id=? OR to_id=?)";args.add(id);args.add(id);
  if(prefix.equals("plant")&&!admin){
   args.clear();long plantId=Long.parseLong(id.substring(6));
   base="SELECT CONCAT('pi:',p.id) AS id,CONCAT('plant:',p.id) AS from_id,CONCAT('taxon:',p.taxon_id) AS to_id,'IDENTITY_REFERENCE' AS relation_type,'目录分类身份引用' AS display_label,'SOURCE_REPORTED' AS verification_state,IF(p.is_public=1,'METADATA_ONLY','CANDIDATE') AS publication_state,p.source_id,p.locator FROM graph_catalog p WHERE p.id=? AND p.taxon_id IS NOT NULL"+(admin?"":" AND p.is_public=1");args.add(plantId);
   base+=" UNION ALL SELECT CONCAT('pn:',n.id),CONCAT('plant:',n.plant_id),CONCAT('pname:',n.id),'HAS_NAME','目录记录名称','SOURCE_REPORTED',IF(p.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED'),'METADATA_ONLY','CANDIDATE'),p.source_id,e.locator FROM plant_name n JOIN graph_catalog p ON p.id=n.plant_id LEFT JOIN evidence e ON e.id=n.evidence_id WHERE n.plant_id=? AND p.id=?"+(admin?"":" AND p.is_public=1 AND n.review_state IN ('APPROVED','VERIFIED_FROM_PRINTED_HEADING','SOURCE_REPORTED')");args.add(plantId);args.add(plantId);
   // The already-authorized center label is the canonical taxon name. A redundant
   // bound predicate lets TiDB use the name index before expanding public-source views.
   base+=" UNION ALL SELECT CONCAT('source-name:',p.id,':',f.id),CONCAT('plant:',p.id),CONCAT('candidate:',f.id),'SOURCE_NAME_RECORD','原文学名精确相同的来源记录',f.knowledge_confidence,'SOURCE_PUBLIC',f.source_id,f.locator FROM graph_catalog p JOIN taxon t ON t.id=p.taxon_id JOIN scientific_candidate c ON c.graph_scientific_name=LEFT(t.canonical_name,191) COLLATE utf8mb4_bin JOIN public_source_record f ON f.id=c.id WHERE p.id=? AND p.is_public=1 AND c.graph_scientific_name=LEFT(?,191) COLLATE utf8mb4_bin AND BINARY JSON_UNQUOTE(JSON_EXTRACT(f.normalized_payload,'$.scientific_name'))=BINARY t.canonical_name";args.add(plantId);args.add(center.displayLabel());
   if(admin){base+=" UNION ALL SELECT CONCAT('course-plant:',sm.id),CONCAT('plant:',sm.plant_id),CONCAT('course-sample:',sm.id),'HAS_RESEARCH_RECORD','目录登记的课程样本（待复核）','SOURCE_REPORTED','CANDIDATE',CONCAT('file:',e.source_asset_id),e.locator FROM research_sample sm LEFT JOIN evidence e ON e.id=sm.evidence_id WHERE sm.plant_id=?";args.add(plantId);}
  }
  if(!admin&&!prefix.equals("plant"))base=publicEdges(prefix,id.substring(id.indexOf(':')+1),args,last,relation,type,state,publication,size,sourceQueries);
  if(!admin&&prefix.equals("trait")){
   args.clear();base="SELECT "+fields+" FROM graph_trait_edge WHERE 1=0";
  }
  if(prefix.equals("candidate")||prefix.equals("trait")){
   String[] parts=id.split(":");base+=" UNION ALL SELECT "+fields+" FROM graph_trait_edge WHERE candidate_id=?";args.add(parts[1]);
   if(prefix.equals("trait")){base+=" AND field_key=? AND ordinality=?";args.add(parts[2]);args.add(Integer.parseInt(parts[3]));}
  }
  if(type!=null){base="SELECT e.*,IF(e.from_id=?,e.to_id,e.from_id) AS neighbor_id FROM ("+base+") e";args.addFirst(id);}
  args.add(last);
  where+=" AND relation_type IN ("+String.join(",",Collections.nCopies(possible.size(),"?"))+")";args.addAll(possible);
  if(prefix.equals("source")){where+=" AND source_id=?";args.add(id.substring(7));}
  if(relation!=null){where+=" AND relation_type=?";args.add(relation);}if(state!=null){where+=" AND verification_state=?";args.add(state);}
  if(publication!=null){where+=" AND publication_state=?";args.add(publication);}
  if(type!=null){
   if(Set.of("MEASUREMENT","MORPHOLOGY","TAXONOMY").contains(type)){where+=" AND neighbor_id LIKE 'candidate:%' AND EXISTS(SELECT 1 FROM scientific_candidate nt WHERE nt.id=SUBSTRING(ge.neighbor_id,11) AND nt.pipeline=?)";args.add(type.equals("MEASUREMENT")?"LIPID":type);}
   else if(type.equals("NAME"))where+=" AND (neighbor_id LIKE 'pname:%' OR neighbor_id LIKE 'tname:%')";
   else {where+=" AND neighbor_id LIKE ?";args.add(type.toLowerCase(Locale.ROOT).replace('_','-')+":%");}
  }
  args.add(size+1);
  List<Map<String,Object>> rows;
  if(!sourceQueries.isEmpty()){
   // Each native branch retains the 5s database timeout and returns at most limit+1.
   // Merge bounded pages in memory instead of materializing a whole large source union.
   var merged=new ArrayList<Map<String,Object>>();
   sourceQueries.sort(Comparator.comparing(EdgeQuery::sortPrefix));
   for(var query:sourceQueries){merged.addAll(jdbc.queryForList(query.sql(),query.parameters().toArray()));if(merged.size()>size)break;}
   rows=merged.stream().sorted(Comparator.comparing(r->text(r,"id"))).limit(size+1L).toList();
  }else rows=jdbc.queryForList("SELECT * FROM ("+base+") ge WHERE id>?"+scope(admin)+where+" ORDER BY id LIMIT ?",args.toArray());
  boolean more=rows.size()>size;var edges=new ArrayList<Edge>();var nodes=new LinkedHashMap<String,Node>();nodes.put(id,center);
  for(var row:rows.stream().limit(size).toList()){
   String from=text(row,"from_id"),to=text(row,"to_id");
   // The node boundary is checked as well as edge visibility. No hidden endpoint is returned.
   for(String endpoint:List.of(from,to))if(!nodes.containsKey(endpoint))nodes.put(endpoint,detail(admin,endpoint));
   edges.add(new Edge(text(row,"id"),from,to,text(row,"relation_type"),text(row,"display_label"),text(row,"verification_state"),text(row,"publication_state"),references(row)));
  }
  return new Page(new ArrayList<>(nodes.values()),edges,id,more?cursor(context,text(rows.get(size-1),"id")):null,more,41,40,view(admin),NOTICE);
 }
 @org.springframework.transaction.annotation.Transactional(readOnly=true,timeout=35)
 public Meta meta(){return cachedMeta(false);}
 @PreAuthorize("hasRole('ADMIN')") @org.springframework.transaction.annotation.Transactional(readOnly=true,timeout=35)
 public Meta adminMeta(){return cachedMeta(true);}
 private Meta cachedMeta(boolean admin){
  if(!admin){var saved=jdbc.queryForList("SELECT computed_at,node_counts,edge_counts FROM graph_statistics_snapshot WHERE scope='PUBLIC' ORDER BY id DESC LIMIT 1");if(!saved.isEmpty()){
   var row=saved.getFirst();var nodes=new LinkedHashMap<String,Long>();var edges=new LinkedHashMap<String,Long>();PublicScientificData.object(row.get("node_counts")).forEach((k,v)->nodes.put(k,((Number)v).longValue()));PublicScientificData.object(row.get("edge_counts")).forEach((k,v)->edges.put(k,((Number)v).longValue()));
   return new Meta("PUBLIC",NODE_TYPES,RELATION_TYPES,nodes,edges,jdbc.queryForObject("SELECT COUNT(*) FROM published_scientific_fact",Long.class),NOTICE+" 全库数量为发布批次统计快照，计算于 "+row.get("computed_at")+"；节点、关系和正式事实数量始终按当前权限查询。该快照不代表科学审核。");
  }}
  String revision=jdbc.queryForObject("SELECT CAST(JSON_ARRAY((SELECT COALESCE(MAX(id),0) FROM scientific_decision),(SELECT COUNT(*) FROM scientific_decision),(SELECT COALESCE(MAX(id),0) FROM review_event),(SELECT COALESCE(SUM(CRC32(CONCAT(id,license_state,integrity_state,sha256))),0) FROM scientific_source_version),(SELECT COALESCE(SUM(CRC32(CONCAT(candidate_id,visibility,knowledge_confidence,content_sha256,source_sha256))),0) FROM source_record_access),(SELECT COALESCE(SUM(CRC32(CONCAT(id,visibility_state,review_state,COALESCE(taxon_id,0)))),0) FROM plant),(SELECT COALESCE(MAX(id),0) FROM source_asset),(SELECT COUNT(*) FROM scientific_candidate),(SELECT COALESCE(SUM(CRC32(projection_sha256)),0) FROM ontology_projection)) AS CHAR)",String.class);
  MetaSnapshot old=admin?adminSnapshot:publicSnapshot;long now=System.nanoTime();
  if(old!=null&&Objects.equals(old.revision(),revision)&&now-old.at()<300_000_000_000L)return old.value();
  if(!metaLock.tryLock())throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"全库统计正在计算；节点探索仍可独立使用，请稍后重试统计");
  try{
   old=admin?adminSnapshot:publicSnapshot;
   if(old!=null&&Objects.equals(old.revision(),revision)&&System.nanoTime()-old.at()<300_000_000_000L)return old.value();
   Meta result=meta(admin);
   result=new Meta(result.view(),result.nodeTypes(),result.relationTypes(),result.nodeCounts(),result.edgeCounts(),result.publishedFactCount(),result.notice()+" 全库统计快照："+java.time.Instant.now()+"；最多缓存5分钟，审核/可见性版本变化时重新计算。节点和关系查询始终重新校验当前权限。");
   MetaSnapshot snapshot=new MetaSnapshot(revision,System.nanoTime(),result);if(admin)adminSnapshot=snapshot;else publicSnapshot=snapshot;return result;
  }finally{metaLock.unlock();}
 }
 private Meta meta(boolean admin){
  if(!admin&&metadataOnly())return new Meta("PUBLIC",NODE_TYPES,RELATION_TYPES,counts("SELECT 'SOURCE' AS kind,COUNT(*) AS n FROM scientific_source_version UNION ALL SELECT 'FILE',COUNT(*) FROM source_asset a WHERE EXISTS(SELECT 1 FROM scientific_source_version s WHERE s.sha256=a.sha256)"),counts("SELECT 'SOURCE_FILE' AS kind,COUNT(*) AS n FROM scientific_source_version s JOIN source_asset a ON a.sha256=s.sha256"),0,NOTICE);
  Map<String,Long> nodes;
  long traitCount=0;
  if(admin){
   nodes=counts("SELECT 'SOURCE' AS kind,COUNT(*) AS n FROM scientific_source_version UNION ALL SELECT 'FILE',COUNT(*) FROM source_asset UNION ALL SELECT 'PLANT',COUNT(*) FROM plant UNION ALL SELECT 'TAXON',COUNT(*) FROM taxon UNION ALL SELECT 'NAME',(SELECT COUNT(*) FROM plant_name)+(SELECT COUNT(*) FROM taxon_name) UNION ALL SELECT 'SAMPLE',COUNT(*) FROM scientific_sample UNION ALL SELECT 'COURSE_SAMPLE',COUNT(*) FROM research_sample UNION ALL SELECT 'COURSE_MEASUREMENT',COUNT(*) FROM measurement UNION ALL SELECT IF(pipeline='LIPID','MEASUREMENT',pipeline),COUNT(*) FROM scientific_candidate GROUP BY pipeline");
   String terms=String.join("+",List.of("leaf_shape","leaf_margin","flower_colour","growth_form","fruit_type","fruit_shape").stream().map(k->"IF(JSON_TYPE(JSON_EXTRACT(o.projection,'$.traits."+k+"'))='ARRAY',JSON_LENGTH(JSON_EXTRACT(o.projection,'$.traits."+k+"')),0)").toList());
   traitCount=jdbc.queryForObject("SELECT COALESCE(SUM("+terms+"),0) FROM ontology_projection o JOIN scientific_candidate c ON c.id=o.candidate_id WHERE c.pipeline='MORPHOLOGY'",Long.class);nodes.put("TRAIT",traitCount);
  }else nodes=counts("SELECT node_type AS kind,record_count AS n FROM graph_public_node_count");
  var edges=admin?counts("SELECT relation_type AS kind,COUNT(*) AS n FROM graph_edge WHERE relation_type<>'HAS_TRAIT' GROUP BY relation_type"):counts("SELECT relation_type AS kind,record_count AS n FROM graph_public_edge_count");
  if(admin)edges.put("HAS_TRAIT",traitCount);

  return new Meta(view(admin),NODE_TYPES,RELATION_TYPES,nodes,edges,jdbc.queryForObject("SELECT COUNT(*) FROM published_scientific_fact",Long.class),NOTICE);
 }
 private boolean metadataOnly(){return Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT EXISTS(SELECT 1 FROM public_source_record) AND NOT EXISTS(SELECT 1 FROM graph_catalog WHERE is_public=1)",Boolean.class));}
 private Map<String,Long> counts(String sql){var out=new LinkedHashMap<String,Long>();for(var r:jdbc.queryForList(sql))out.put(text(r,"kind"),((Number)r.get("n")).longValue());return out;}
 private Node node(Map<String,Object> row){
  var attributes=PublicScientificData.object(row.get("attributes"));var refs=references(row);
  if("FILE".equals(row.get("node_type"))){refs=jdbc.queryForList("SELECT id,SUBSTRING_INDEX(dataset,'/',-1) AS label,sha256,version_label,license_state FROM scientific_source_version WHERE sha256=? ORDER BY id LIMIT 40",attributes.get("sha256")).stream().map(s->new SourceReference(text(s,"id"),text(s,"label"),text(s,"sha256"),safeVersion(text(s,"version_label")),text(s,"license_state"),Map.of())).toList();if(refs.isEmpty())refs=fileReference(text(row,"id"),Map.of());}
  if(attributes.containsKey("version"))attributes.put("version",safeVersion(String.valueOf(attributes.get("version"))));
  return new Node(text(row,"id"),text(row,"node_type"),text(row,"display_label"),text(row,"verification_state"),text(row,"publication_state"),refs,attributes);
 }
 private List<SourceReference> references(Map<String,Object> row){
  // A newly sourced name has its own evidence. Its plant's catalogue source is not a name citation.
  String recordId=text(row,"id"),nameId=null;
  if(recordId!=null&&(recordId.startsWith("pname:")||recordId.startsWith("pn:")))nameId=recordId.substring(recordId.indexOf(':')+1);
  else if(row.containsKey("plant_id")&&row.containsKey("name_text"))nameId=recordId;
  if(nameId!=null&&nameId.matches("[0-9]{1,18}")){
   var names=jdbc.queryForList("SELECT s.id AS asset_id,SUBSTRING_INDEX(s.relative_path,'/',-1) AS label,s.sha256,a.source_version,e.locator FROM botanical_name_assertion a JOIN plant_name n ON n.id=a.plant_name_id JOIN evidence e ON e.id=n.evidence_id JOIN source_asset s ON s.id=e.source_asset_id WHERE a.plant_name_id=? ORDER BY a.id LIMIT 6",Long.parseLong(nameId));
   if(!names.isEmpty())return names.stream().map(n->new SourceReference("file:"+n.get("asset_id"),text(n,"label"),text(n,"sha256"),safeVersion(text(n,"source_version")),"UNKNOWN",safeLocator(n.get("locator")))).toList();
  }
  String source=text(row,"source_id");if(source==null)return List.of();
  if(source.startsWith("file:"))return fileReference(source,safeLocator(row.get("locator")));
  var sources=jdbc.queryForList("SELECT id,SUBSTRING_INDEX(dataset,'/',-1) AS label,sha256,version_label,license_state FROM scientific_source_version WHERE id=?",source);
  return sources.stream().map(s->new SourceReference(text(s,"id"),text(s,"label"),text(s,"sha256"),safeVersion(text(s,"version_label")),text(s,"license_state"),safeLocator(row.get("locator")))).toList();
 }
 private List<SourceReference> fileReference(String id,Object locator){return jdbc.queryForList("SELECT id,SUBSTRING_INDEX(relative_path,'/',-1) AS label,sha256 FROM source_asset WHERE id=?",id.substring(5)).stream().map(s->new SourceReference("file:"+s.get("id"),text(s,"label"),text(s,"sha256"),null,"UNKNOWN",locator)).toList();}
 private static String safeVersion(String value){return value!=null&&(value.startsWith("/")||value.contains(":\\")||value.startsWith("file:"))?"VERSION_REDACTED":value;}
 private static Object safeLocator(Object locator){
  var clean=PublicScientificData.object(PublicScientificData.rows(List.of(Map.of("locator",locator==null?"{}":locator))).getFirst().get("locator"));
  for(String key:List.of("archive_member","zip_member")){Object v=clean.get(key);if(v instanceof String s)clean.put(key,s.replace('\\','/').substring(s.replace('\\','/').lastIndexOf('/')+1));}
  return clean;
 }
 private static String text(Map<String,Object> row,String key){Object v=row.get(key);return v==null?null:v.toString();}
}
