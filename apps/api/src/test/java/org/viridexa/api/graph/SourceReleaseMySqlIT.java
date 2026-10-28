package org.viridexa.api.graph;

import java.net.http.HttpClient;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

/** Only the guarded isolated schema; no fixture release reaches the production catalog. */
@org.springframework.boot.test.context.SpringBootTest(webEnvironment=org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT)
class SourceReleaseMySqlIT {
 @org.springframework.boot.test.web.server.LocalServerPort int port;
 @org.springframework.test.context.DynamicPropertySource static void config(org.springframework.test.context.DynamicPropertyRegistry r){GraphMySqlIT.configuration(r);r.add("viridexa.accounts.username",()->GraphMySqlIT.env("GOVERNANCE_TEST_ACCOUNT"));r.add("viridexa.accounts.password",()->GraphMySqlIT.env("GOVERNANCE_TEST_PASSWORD"));}
 @org.springframework.beans.factory.annotation.Autowired GraphService graphService;
 final GraphMySqlIT fixture=new GraphMySqlIT();
 void sql(String q)throws Exception{fixture.sql(q);}
 String scalar(String q)throws Exception{return fixture.scalar(q);}
 String candidate(String kind)throws Exception{return fixture.candidate(kind);}
 HttpClient client(){return fixture.client();}
 java.net.http.HttpResponse<String> get(HttpClient c,String path)throws Exception{fixture.port=port;return fixture.get(c,path);}
 @BeforeEach void reset()throws Exception{fixture.resetAndAddMetadataOnlyFileFixture();}

 String id;
 @BeforeEach void choose()throws Exception{ id=candidate("OIL_CONTENT"); }
 @AfterEach void removeRelease()throws Exception{sql("DELETE FROM source_release_event WHERE candidate_id='"+id+"'");sql("DELETE FROM source_record_access WHERE candidate_id='"+id+"'");}
 void release(String confidence)throws Exception{sql("INSERT INTO source_record_access SELECT c.id,'PUBLIC','"+confidence+"',c.content_sha256,s.sha256,'ISOLATED_TECHNICAL_TEST','ISOLATED_TEST_FIXTURE',CURRENT_TIMESTAMP(6) FROM scientific_candidate c JOIN scientific_source_version s ON s.id=c.source_id WHERE c.id='"+id+"'");}
 @Test void sourceReleaseIsVisibleWithoutHumanDecision()throws Exception{
  release("SOURCE_REPORTED");var c=client();var r=get(c,"/api/v1/graph/nodes/candidate:"+id);assertEquals(200,r.statusCode(),r.body());assertTrue(r.body().contains("SOURCE_PUBLIC"));assertTrue(r.body().contains("SOURCE_REPORTED"));assertEquals("0",scalar("SELECT COUNT(*) FROM published_scientific_fact"));assertEquals("0",scalar("SELECT COUNT(*) FROM scientific_decision"));assertEquals("CANDIDATE",scalar("SELECT normalization_state FROM scientific_candidate WHERE id='"+id+"'"));
 }
 @Test void sourceCandidateExpansionReturnsRealEndpointsAndFilters()throws Exception{
  release("SOURCE_REPORTED");var c=client();
  assertFalse(graphService.neighbors("candidate:"+id,null,null,null,20,null,1).edges().isEmpty());
  var r=get(c,"/api/v1/graph/nodes/candidate:"+id+"/neighbors?limit=20");assertEquals(200,r.statusCode(),r.body());assertTrue(r.body().contains("HAS_MEASUREMENT"));assertTrue(r.body().contains("SOURCED_FROM"));
  var filtered=get(c,"/api/v1/graph/nodes/candidate:"+id+"/neighbors?limit=20&relationType=HAS_MEASUREMENT");assertEquals(200,filtered.statusCode(),filtered.body());assertTrue(filtered.body().contains("HAS_MEASUREMENT"));assertTrue(filtered.body().contains("SOURCE_PUBLIC"));
  String source=scalar("SELECT source_id FROM scientific_candidate WHERE id='"+id+"'");
  var sourcePage=get(c,"/api/v1/graph/nodes/source:"+source+"/neighbors?limit=1");assertEquals(200,sourcePage.statusCode(),sourcePage.body());assertTrue(sourcePage.body().contains("nextCursor"));
  var measurementPage=get(c,"/api/v1/graph/nodes/source:"+source+"/neighbors?limit=1&nodeType=MEASUREMENT");assertEquals(200,measurementPage.statusCode(),measurementPage.body());assertTrue(measurementPage.body().contains("SOURCE_PUBLIC"));
  sql("UPDATE source_record_access SET visibility='INTERNAL' WHERE candidate_id='"+id+"'");assertEquals(404,get(c,"/api/v1/graph/nodes/candidate:"+id+"/neighbors").statusCode());
 }
 @Test void unexposedCandidateRemainsHidden()throws Exception{assertEquals(404,get(client(),"/api/v1/graph/nodes/candidate:"+id).statusCode());}
 @Test void changedContentInvalidatesRelease()throws Exception{release("MACHINE_EXTRACTED");String old=scalar("SELECT content_sha256 FROM scientific_candidate WHERE id='"+id+"'");try{sql("UPDATE scientific_candidate SET content_sha256=REPEAT('a',64) WHERE id='"+id+"'");assertEquals("0",scalar("SELECT COUNT(*) FROM public_source_record WHERE id='"+id+"'"));}finally{sql("UPDATE scientific_candidate SET content_sha256='"+old+"' WHERE id='"+id+"'");}}
 @Test void restrictiveVisibilityAndLicenseHideRecords()throws Exception{release("SOURCE_REPORTED");sql("UPDATE source_record_access SET visibility='INTERNAL' WHERE candidate_id='"+id+"'");assertEquals(404,get(client(),"/api/v1/graph/nodes/candidate:"+id).statusCode());sql("UPDATE source_record_access SET visibility='PUBLIC' WHERE candidate_id='"+id+"'");String source=scalar("SELECT source_id FROM scientific_candidate WHERE id='"+id+"'");sql("UPDATE scientific_source_version SET license_state='RESTRICTED' WHERE id='"+source+"'");assertEquals(404,get(client(),"/api/v1/graph/nodes/candidate:"+id).statusCode());}
 @Test void rejectionCannotBeBypassedBySourceAccess()throws Exception{release("SOURCE_REPORTED");sql("INSERT INTO scientific_review_state(candidate_id,state) VALUES('"+id+"','REJECTED')");assertEquals(404,get(client(),"/api/v1/graph/nodes/candidate:"+id).statusCode());}
 @Test void rawPayloadAndPrivatePathsStayPrivate()throws Exception{release("SOURCE_REPORTED");var r=get(client(),"/api/v1/science/lineage/"+id);assertEquals(200,r.statusCode());assertTrue(r.body().contains("knowledge_confidence"));assertFalse(r.body().contains("raw_payload"));assertFalse(r.body().contains("/Users/"));assertFalse(r.body().contains("rationale"));}
 @Test void publicReleaseStillRequiresLoginForWorkbench()throws Exception{release("SOURCE_REPORTED");assertEquals(401,get(client(),"/api/v1/workbench/samples").statusCode());assertEquals(401,get(client(),"/api/v1/admin/science/candidates").statusCode());}
 @Test void releaseCannotClaimHumanVerification()throws Exception{assertThrows(java.sql.SQLException.class,()->release("HUMAN_VERIFIED"));}
 @Test void aggregateCacheInvalidatesOnRecordedWithdrawalAndLicenseChange()throws Exception{
  release("SOURCE_REPORTED");
  var parser=org.springframework.boot.json.JsonParserFactory.getJsonParser();var c=client();
  var first=get(c,"/api/v1/showcase");assertEquals(200,first.statusCode());
  assertTrue(first.headers().firstValue("Cache-Control").orElse("").contains("no-store"));
  var before=(java.util.Map<?,?>)parser.parseMap(first.body()).get("counts");
  long count=((Number)before.get("publicSourceRecords")).longValue();assertTrue(count>0);
  sql("UPDATE source_record_access SET visibility='INTERNAL' WHERE candidate_id='"+id+"'");
  sql("INSERT INTO source_release_event(event_key,candidate_id,visibility,authorization_reference,payload) VALUES(SHA2(UUID(),256),'"+id+"','INTERNAL','ISOLATED_TECHNICAL_TEST',JSON_OBJECT('scope','CACHE_WITHDRAWAL_TEST'))");
  var after=(java.util.Map<?,?>)parser.parseMap(get(c,"/api/v1/showcase").body()).get("counts");
  assertEquals(count-1,((Number)after.get("publicSourceRecords")).longValue());
  assertEquals(404,get(c,"/api/v1/graph/nodes/candidate:"+id).statusCode());
  sql("UPDATE source_record_access SET visibility='PUBLIC' WHERE candidate_id='"+id+"'");
  String source=scalar("SELECT source_id FROM scientific_candidate WHERE id='"+id+"'");
  sql("UPDATE scientific_source_version SET license_state='RESTRICTED' WHERE id='"+source+"'");
  var restricted=(java.util.Map<?,?>)parser.parseMap(get(c,"/api/v1/showcase").body()).get("counts");
  assertEquals(count-1,((Number)restricted.get("publicSourceRecords")).longValue());
  assertEquals(404,get(c,"/api/v1/graph/nodes/candidate:"+id).statusCode());
 }
 @Test void runtimeMetricsAreAdminOnlyAndNeverPartOfShowcase()throws Exception{
  var c=client();assertEquals(401,get(c,"/actuator/metrics").statusCode());
  fixture.port=port;
  var login=fixture.post(c,"/api/v1/account/login","{\"email\":\"researcher-fixture@example.invalid\",\"password\":\""+GraphMySqlIT.env("GOVERNANCE_FIXTURE_PASSWORD")+"\"}",true);
  assertEquals(200,login.statusCode());assertEquals(403,get(c,"/actuator/metrics").statusCode());
  var admin=fixture.admin();assertEquals(200,get(admin,"/actuator/metrics").statusCode());
  String body=get(c,"/api/v1/showcase").body();
  assertFalse(body.contains("jvm.memory"));assertFalse(body.contains("hikaricp"));assertFalse(body.contains("/Users/"));
 }
 @Test void indexedCatalogReleaseStillObeysLatestWithdrawal()throws Exception{
  String key=fixture.digest("source-display-isolated-"+java.util.UUID.randomUUID());String taxon=fixture.digest(key+"taxon");
  String evidence=scalar("SELECT evidence_id FROM taxon WHERE evidence_id IS NOT NULL ORDER BY id LIMIT 1");
  sql("INSERT INTO taxon(taxon_key,taxon_rank,canonical_name,identity_state,evidence_id) VALUES('"+taxon+"','UNRANKED','Isolated source name','SOURCE_REPORTED',"+evidence+")");
  String tid=scalar("SELECT id FROM taxon WHERE taxon_key='"+taxon+"'");
  sql("INSERT INTO plant(record_key,taxon_id,review_state,visibility_state) VALUES('"+key+"',"+tid+",'REVIEW_PENDING','PUBLIC')");String pid=scalar("SELECT id FROM plant WHERE record_key='"+key+"'");
  try{
   sql("INSERT INTO review_event(event_key,entity_type,entity_key,action,resulting_state,raw_payload) SELECT SHA2('"+key+"release',256),'PLANT','"+key+"','CATALOG_METADATA_RELEASE','METADATA_ONLY',JSON_OBJECT('scope','CATALOG_METADATA_ONLY','source_sha256',s.sha256,'scientific_name',t.canonical_name) FROM taxon t JOIN evidence e ON e.id=t.evidence_id JOIN source_asset a ON a.id=e.source_asset_id JOIN scientific_source_version s ON s.sha256=a.sha256 WHERE t.id="+tid+" LIMIT 1");
   sql("INSERT INTO catalog_display_access SELECT p.id,r.id,s.id,s.sha256,t.canonical_name FROM plant p JOIN taxon t ON t.id=p.taxon_id JOIN evidence e ON e.id=t.evidence_id JOIN source_asset a ON a.id=e.source_asset_id JOIN scientific_source_version s ON s.sha256=a.sha256 JOIN review_event r ON r.entity_key=p.record_key WHERE p.id="+pid+" LIMIT 1");
   String nameAsset=fixture.digest(key+"name-asset"),nameEvidence=fixture.digest(key+"name-evidence"),nameKey=fixture.digest(key+"name"),nameHash=fixture.digest("ISOLATED NAME CSV CONTENT");
   sql("INSERT INTO source_asset(asset_key,relative_path,sha256,size_bytes,asset_kind) VALUES('"+nameAsset+"','isolated/name-metadata.csv','"+nameHash+"',25,'NAME_METADATA')");
   sql("INSERT INTO evidence(evidence_key,source_asset_id,locator_type,locator) SELECT '"+nameEvidence+"',id,'WORKBOOK_ROW',JSON_OBJECT('row',2,'sheet','ISOLATED_TEST') FROM source_asset WHERE asset_key='"+nameAsset+"'");
   sql("INSERT INTO plant_name(name_key,plant_id,name_role,language_tag,name_text,review_state,evidence_id) SELECT '"+nameKey+"',"+pid+",'COMMON','zh','隔离中文名','SOURCE_REPORTED',id FROM evidence WHERE evidence_key='"+nameEvidence+"'");
   String nameId=scalar("SELECT id FROM plant_name WHERE name_key='"+nameKey+"'");
   sql("INSERT INTO botanical_name_assertion(id,plant_name_id,source_url,source_version,authority_taxon_id,matched_scientific_name,match_method,confidence,display_basis,queried_at) VALUES(SHA2('"+nameKey+"assertion',256),"+nameId+",'https://example.invalid/names','ISOLATED_NAME_TEST','fixture-taxon','Isolated source name','ACCEPTED_EXACT','SOURCE_REPORTED','ISOLATED_TEST',CURRENT_TIMESTAMP(6))");
   var nameNode=get(client(),"/api/v1/graph/nodes/pname:"+nameId);assertEquals(200,nameNode.statusCode(),nameNode.body());assertTrue(nameNode.body().contains(nameHash));assertTrue(nameNode.body().contains("ISOLATED_NAME_TEST"));assertFalse(nameNode.body().contains("isolated/name-metadata.csv"));
   var nameEdges=get(client(),"/api/v1/graph/nodes/plant:"+pid+"/neighbors?relationType=HAS_NAME");assertEquals(200,nameEdges.statusCode(),nameEdges.body());assertTrue(nameEdges.body().contains(nameHash));
   var chinese=get(client(),"/api/v1/plants?query="+java.net.URLEncoder.encode("隔离中文名",java.nio.charset.StandardCharsets.UTF_8));assertEquals(200,chinese.statusCode());assertTrue(chinese.body().contains("\"id\":"+pid));
   assertEquals(200,get(client(),"/api/v1/plants/"+pid).statusCode());assertEquals("0",scalar("SELECT COUNT(*) FROM scientific_decision"));
   var entrances=get(client(),"/api/v1/graph/search?q=Isolated%20source%20name&limit=1");assertEquals(200,entrances.statusCode(),entrances.body());assertTrue(entrances.body().contains("plant:"+pid));
   sql("INSERT INTO review_event(event_key,entity_type,entity_key,action,resulting_state) VALUES(SHA2('"+key+"withdraw',256),'PLANT','"+key+"','CATALOG_WITHDRAW','REVIEW_PENDING')");
   assertEquals(404,get(client(),"/api/v1/plants/"+pid).statusCode());
  }finally{sql("DELETE a FROM botanical_name_assertion a JOIN plant_name n ON n.id=a.plant_name_id WHERE n.plant_id="+pid);sql("DELETE FROM plant_name WHERE plant_id="+pid);sql("DELETE FROM evidence WHERE evidence_key=SHA2('"+key+"name-evidence',256)");sql("DELETE FROM source_asset WHERE asset_key=SHA2('"+key+"name-asset',256)");sql("DELETE FROM catalog_display_access WHERE plant_id="+pid);sql("DELETE FROM review_event WHERE entity_key='"+key+"'");sql("DELETE FROM plant WHERE id="+pid);sql("DELETE FROM taxon WHERE id="+tid);}
 }

 @Test void authenticatedNonAdminReadsSourcePointsWithoutScientificApproval()throws Exception{
  release("SOURCE_REPORTED");fixture.port=port;var member=client();
  var login=fixture.post(member,"/api/v1/account/login","{\"email\":\"researcher-fixture@example.invalid\",\"password\":\""+GraphMySqlIT.env("GOVERNANCE_FIXTURE_PASSWORD")+"\"}",true);assertEquals(200,login.statusCode());
  String sample=scalar("SELECT sample_id FROM scientific_lipid_observation WHERE candidate_id='"+id+"'");
  var points=get(member,"/api/v1/workbench/points?sampleIds=sample:"+sample);assertEquals(200,points.statusCode(),points.body());assertTrue(points.body().contains("SOURCE_PUBLIC"));assertTrue(points.body().contains("SOURCE_REPORTED"));
  assertEquals(200,get(member,"/api/v1/workbench/points/candidate:"+id+"/evidence").statusCode());
  assertEquals(403,get(member,"/api/v1/admin/science/candidates").statusCode());assertEquals("0",scalar("SELECT COUNT(*) FROM published_scientific_fact"));
 }

}
