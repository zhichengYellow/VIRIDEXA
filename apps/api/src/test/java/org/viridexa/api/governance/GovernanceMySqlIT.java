package org.viridexa.api.governance;

import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.json.JsonParserFactory;
import static org.junit.jupiter.api.Assertions.*;

/** Explicitly invoked against .env.governance-test only; no production fixture writes. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class GovernanceMySqlIT {
 @LocalServerPort int port;
 static final String EMAIL="automated-isolated-fixture@example.invalid";
 static String env(String key){String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalStateException("Missing isolated test environment "+key);return value;}
 @DynamicPropertySource static void configuration(DynamicPropertyRegistry r){
  if(!env("GOVERNANCE_TEST_SCHEMA").startsWith("viridexa_governance_test_"))throw new IllegalStateException("Production schema forbidden");
  r.add("spring.datasource.url",()->env("GOVERNANCE_TEST_DB_URL"));r.add("spring.datasource.username",()->env("GOVERNANCE_TEST_RO"));r.add("spring.datasource.password",()->env("GOVERNANCE_TEST_PASSWORD"));
  r.add("spring.flyway.user",()->env("GOVERNANCE_TEST_MIGRATOR"));r.add("spring.flyway.password",()->env("GOVERNANCE_TEST_PASSWORD"));
  r.add("viridexa.review.username",()->env("GOVERNANCE_TEST_WRITER"));r.add("viridexa.review.password",()->env("GOVERNANCE_TEST_PASSWORD"));r.add("viridexa.protected-root",()->env("VIRIDEXA_PROTECTED_ROOT"));
 }
 Connection connection()throws Exception{return DriverManager.getConnection(env("GOVERNANCE_TEST_DB_URL"),env("GOVERNANCE_TEST_MIGRATOR"),env("GOVERNANCE_TEST_PASSWORD"));}
 void sql(String query)throws Exception{try(var c=connection();var statement=c.createStatement()){statement.execute(query);}}
 String scalar(String query)throws Exception{try(var c=connection();var statement=c.createStatement();var rs=statement.executeQuery(query)){assertTrue(rs.next());return rs.getString(1);}}
 String candidate(String kind)throws Exception{return scalar("SELECT candidate_id FROM scientific_lipid_observation WHERE kind='"+kind+"' ORDER BY candidate_id LIMIT 1");}
 String base(){return "http://127.0.0.1:"+port;}
 HttpClient client(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();}
 HttpResponse<String> get(HttpClient c,String path)throws Exception{return c.send(HttpRequest.newBuilder(URI.create(base()+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
 Map<String,Object> object(String text){return JsonParserFactory.getJsonParser().parseMap(text);}
 String csrf(HttpClient c)throws Exception{return (String)object(get(c,"/api/v1/csrf").body()).get("token");}
 HttpResponse<String> post(HttpClient c,String path,String body,boolean token)throws Exception{
  var builder=HttpRequest.newBuilder(URI.create(base()+path)).header("Content-Type","application/json");if(token)builder.header("X-CSRF-TOKEN",csrf(c));
  return c.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
 }
 HttpClient admin()throws Exception{var c=client();assertEquals(200,post(c,"/api/v1/admin/session","{\"username\":\""+EMAIL+"\",\"password\":\""+env("GOVERNANCE_FIXTURE_PASSWORD")+"\"}",true).statusCode());return c;}
 String command(String action,int version,String request){return "{\"action\":\""+action+"\",\"expectedVersion\":"+version+",\"rationale\":\"Isolated technical fixture only; no human scientific review\",\"requestId\":\""+request+"\"}";}
 String path(String id){return "/api/v1/admin/science/candidates/"+id+"/decisions";}
 @BeforeEach void reset()throws Exception{
  sql("DELETE FROM scientific_decision");sql("DELETE FROM scientific_review_state");sql("DELETE FROM source_license_event");sql("DELETE FROM source_governance");sql("UPDATE scientific_source_version SET license_state='PERMITTED'");sql("UPDATE scientific_lipid_observation SET unit='%' WHERE kind<>'FOOD_NUTRIENT'");
 }
 @Test void approveReplayAndRevokeArePublishedTransactionally()throws Exception{
  var c=admin();String id=candidate("OIL_CONTENT"),request=UUID.randomUUID().toString(),raw=scalar("SELECT content_sha256 FROM scientific_candidate WHERE id='"+id+"'");
  var result=post(c,path(id),command("APPROVED",0,request),true);assertEquals(200,result.statusCode(),result.body());assertEquals("1",scalar("SELECT COUNT(*) FROM published_scientific_fact WHERE id='"+id+"'"));
  assertEquals(true,object(post(c,path(id),command("APPROVED",0,request),true).body()).get("replayed"));assertEquals("1",scalar("SELECT COUNT(*) FROM scientific_decision"));
  assertEquals("1",scalar("SELECT COUNT(*) FROM scientific_decision d JOIN app_user u ON u.id=d.reviewer_id WHERE u.email='"+EMAIL+"' AND decided_at IS NOT NULL"));
  assertEquals(200,post(c,path(id),command("REVOKED",1,UUID.randomUUID().toString()),true).statusCode());assertEquals("0",scalar("SELECT COUNT(*) FROM published_scientific_fact"));assertEquals(raw,scalar("SELECT content_sha256 FROM scientific_candidate WHERE id='"+id+"'"));
 }
 @Test void rejectAndEvidenceReturnRetainHistory()throws Exception{var c=admin();String id=candidate("OIL_CONTENT");assertEquals(200,post(c,path(id),command("REJECTED",0,UUID.randomUUID().toString()),true).statusCode());assertEquals(409,post(c,path(id),command("APPROVED",1,UUID.randomUUID().toString()),true).statusCode());assertEquals(200,post(c,path(id),command("NEEDS_EVIDENCE",1,UUID.randomUUID().toString()),true).statusCode());assertEquals("2",scalar("SELECT COUNT(*) FROM scientific_decision"));assertEquals("0",scalar("SELECT COUNT(*) FROM published_scientific_fact"));}
 @Test void realFameSamplePublishesSeparatelyFromOilContent()throws Exception{
  var c=admin();String fame=candidate("FAME_COMPOSITION"),oil=candidate("OIL_CONTENT");
  assertEquals(200,post(c,path(fame),command("APPROVED",0,UUID.randomUUID().toString()),true).statusCode());
  String sample=scalar("SELECT sample_id FROM scientific_lipid_observation WHERE candidate_id='"+fame+"'");
  assertEquals("1",scalar("SELECT COUNT(*) FROM published_scientific_fact f JOIN scientific_lipid_observation o ON o.candidate_id=f.id WHERE o.kind='FAME_COMPOSITION' AND o.sample_id='"+sample+"' AND o.basis='FAME GC content / %'"));
  assertEquals("0",scalar("SELECT COUNT(*) FROM published_scientific_fact WHERE id='"+oil+"'"));
 }
 @Test void evidencePreviewRequiresLicenseAuthenticationAndVerifiedFile()throws Exception{
  var c=admin();String sid=scalar("SELECT id FROM scientific_source_version WHERE path LIKE '%xiangxi_seed_oils_2018.pdf' LIMIT 1");String preview="/api/v1/admin/science/sources/"+sid+"/preview";
  assertEquals(401,get(client(),preview).statusCode());sql("UPDATE scientific_source_version SET license_state='UNKNOWN' WHERE id='"+sid+"'");assertEquals(403,get(c,preview).statusCode());
  String body="{\"licenseState\":\"PERMITTED\",\"expectedVersion\":0,\"rationale\":\"Isolated technical preview only, no legal determination\",\"requestId\":\""+UUID.randomUUID()+"\"}";
  assertEquals(200,post(c,"/api/v1/admin/science/sources/"+sid+"/license",body,true).statusCode());
  var result=get(c,preview);assertEquals(200,result.statusCode());assertTrue(result.headers().firstValue("Content-Type").orElse("").contains("application/pdf"));assertTrue(result.headers().firstValue("Cache-Control").orElse("").contains("no-store"));assertTrue(result.body().startsWith("%PDF"));
 }
 @Test void concurrentDifferentDecisionsHaveOneWinner()throws Exception{
  var c=admin();String id=candidate("OIL_CONTENT");var pool=Executors.newFixedThreadPool(2);
  try{Callable<Integer> approve=()->post(c,path(id),command("APPROVED",0,UUID.randomUUID().toString()),true).statusCode();var results=pool.invokeAll(List.of(approve,approve));var statuses=new ArrayList<Integer>();for(var result:results)statuses.add(result.get());Collections.sort(statuses);assertEquals(List.of(200,409),statuses);assertEquals("1",scalar("SELECT COUNT(*) FROM scientific_decision"));}finally{pool.shutdownNow();}
 }
 @Test void databaseFailureRollsBackStateAndDecision()throws Exception{
  var c=admin();String id=candidate("OIL_CONTENT");sql("ALTER TABLE scientific_decision ADD CONSTRAINT fixture_fail_decision CHECK (rationale NOT LIKE 'Isolated technical fixture%')");
  try{var response=post(c,path(id),command("APPROVED",0,UUID.randomUUID().toString()),true);assertEquals(503,response.statusCode(),response.body());assertEquals("0",scalar("SELECT COUNT(*) FROM scientific_review_state"));assertEquals("0",scalar("SELECT COUNT(*) FROM scientific_decision"));assertFalse(response.body().contains("injected failure"));}finally{sql("ALTER TABLE scientific_decision DROP CHECK fixture_fail_decision");}
 }
 @Test void inappropriateUnitsAndHeldCandidatesCannotBeApproved()throws Exception{var c=admin();String id=candidate("FAME_COMPOSITION");sql("UPDATE scientific_lipid_observation SET unit='mg/100g' WHERE candidate_id='"+id+"'");assertEquals(422,post(c,path(id),command("APPROVED",0,UUID.randomUUID().toString()),true).statusCode());String held=scalar("SELECT id FROM scientific_candidate WHERE normalization_state='HELD' LIMIT 1");assertEquals(422,post(c,path(held),command("APPROVED",0,UUID.randomUUID().toString()),true).statusCode());assertEquals("0",scalar("SELECT COUNT(*) FROM scientific_decision"));}
 @Test void licenseCorrectionWithdrawsPublishedResultAndRecordsActor()throws Exception{var c=admin();String id=candidate("OIL_CONTENT");assertEquals(200,post(c,path(id),command("APPROVED",0,UUID.randomUUID().toString()),true).statusCode());String sid=scalar("SELECT source_id FROM scientific_candidate WHERE id='"+id+"'");String body="{\"licenseState\":\"RESTRICTED\",\"expectedVersion\":0,\"rationale\":\"Isolated technical license withdrawal only\",\"requestId\":\""+UUID.randomUUID()+"\"}";assertEquals(200,post(c,"/api/v1/admin/science/sources/"+sid+"/license",body,true).statusCode());assertEquals("0",scalar("SELECT COUNT(*) FROM published_scientific_fact"));assertEquals("1",scalar("SELECT COUNT(*) FROM source_license_event"));}
 @Test void wrongStudySampleAssociationCannotPublish()throws Exception{
  var c=admin();String id=candidate("FAME_COMPOSITION"),old=scalar("SELECT sample_id FROM scientific_lipid_observation WHERE candidate_id='"+id+"'"),other=scalar("SELECT id FROM scientific_sample WHERE id<>'"+old+"' LIMIT 1");
  try{sql("UPDATE scientific_lipid_observation SET sample_id='"+other+"' WHERE candidate_id='"+id+"'");assertEquals(422,post(c,path(id),command("APPROVED",0,UUID.randomUUID().toString()),true).statusCode());assertEquals("0",scalar("SELECT COUNT(*) FROM scientific_decision"));}finally{sql("UPDATE scientific_lipid_observation SET sample_id='"+old+"' WHERE candidate_id='"+id+"'");}
 }
 @Test void publicDataIsSafeAndUnapprovedRecordsRemainHidden()throws Exception{var c=admin();String id=candidate("OIL_CONTENT");assertEquals(200,post(c,path(id),command("APPROVED",0,UUID.randomUUID().toString()),true).statusCode());String response=get(client(),"/api/v1/science/lineage/"+id).body();assertTrue(response.contains("sha256"));for(String forbidden:List.of("raw_payload","reviewer_id","rationale",EMAIL,"/Users/"))assertFalse(response.contains(forbidden),forbidden);assertEquals("1",scalar("SELECT COUNT(*) FROM published_scientific_fact"));}
 @Test void csrfRolePaginationAndReadonlyPrivilegesEnforced()throws Exception{
  var c=admin();String id=candidate("OIL_CONTENT");assertEquals(403,post(c,path(id),command("APPROVED",0,UUID.randomUUID().toString()),false).statusCode());assertEquals(400,get(c,"/api/v1/admin/science/candidates?size=1000").statusCode());assertEquals(401,get(client(),"/api/v1/admin/science/candidates").statusCode());
  assertEquals(400,get(c,"/api/v1/admin/science/candidates?sourceId=invalid").statusCode());
  var search=get(c,"/api/v1/admin/science/candidates?species="+URLEncoder.encode("' OR 1=1 --",java.nio.charset.StandardCharsets.UTF_8));assertEquals(200,search.statusCode());assertEquals(0,((Number)object(search.body()).get("total")).intValue());
  var researcher=client();assertEquals(403,post(researcher,"/api/v1/admin/session","{\"username\":\"researcher-fixture@example.invalid\",\"password\":\""+env("GOVERNANCE_FIXTURE_PASSWORD")+"\"}",true).statusCode());
  try(var ro=DriverManager.getConnection(env("GOVERNANCE_TEST_DB_URL"),env("GOVERNANCE_TEST_RO"),env("GOVERNANCE_TEST_PASSWORD"));var statement=ro.createStatement()){assertThrows(SQLException.class,()->statement.execute("UPDATE scientific_candidate SET reason='forbidden' WHERE 1=0"));}
  try(var writer=DriverManager.getConnection(env("GOVERNANCE_TEST_DB_URL"),env("GOVERNANCE_TEST_WRITER"),env("GOVERNANCE_TEST_PASSWORD"));var statement=writer.createStatement()){
   for(String forbidden:List.of("UPDATE scientific_candidate SET reason='forbidden' WHERE 1=0","UPDATE scientific_source_version SET sha256='forbidden' WHERE 1=0","DELETE FROM scientific_decision WHERE 1=0","INSERT INTO app_user SELECT * FROM app_user WHERE 1=0"))assertThrows(SQLException.class,()->statement.execute(forbidden));
  }
 }
 String catalogPath(){return "/api/v1/admin/science/catalog/1/decisions";}
 String catalogCommand(String action,long version,String request,boolean confirmed)throws Exception{
  String name=scalar("SELECT t.canonical_name FROM plant p JOIN taxon t ON t.id=p.taxon_id WHERE p.id=1");
  String sha=scalar("SELECT a.sha256 FROM plant p JOIN taxon t ON t.id=p.taxon_id JOIN evidence e ON e.id=t.evidence_id JOIN source_asset a ON a.id=e.source_asset_id WHERE p.id=1");
  return "{\"action\":\""+action+"\",\"expectedEventId\":"+version+",\"expectedScientificName\":\""+name+"\",\"expectedSourceSha256\":\""+sha+"\",\"scientificNameConfirmed\":"+confirmed+",\"rationale\":\"Isolated technical fixture only; no human scientific review\",\"requestId\":\""+request+"\"}";
 }
 void resetCatalog()throws Exception{sql("DELETE FROM review_event WHERE action LIKE 'CATALOG_%'");sql("UPDATE plant SET review_state='REVIEW_PENDING',visibility_state='RESEARCH_PREVIEW' WHERE id=1");sql("UPDATE taxon t JOIN plant p ON p.taxon_id=t.id SET t.identity_state='VERIFIED_FROM_PRINTED_HEADING' WHERE p.id=1");}
 @Test void catalogReleaseReplayAndWithdrawNeverApproveObservations()throws Exception{
  resetCatalog();try{var c=admin();String body=catalogCommand("RELEASE",0,UUID.randomUUID().toString(),true);var released=post(c,catalogPath(),body,true);assertEquals(200,released.statusCode(),released.body());assertEquals(true,object(post(c,catalogPath(),body,true).body()).get("replayed"));assertEquals("1",scalar("SELECT COUNT(*) FROM graph_catalog WHERE is_public=1"));assertEquals("0",scalar("SELECT COUNT(*) FROM scientific_decision"));assertEquals("0",scalar("SELECT COUNT(*) FROM published_scientific_fact"));assertTrue(get(client(),"/api/v1/plants?query=Cycas").body().contains("Cycas"));long event=((Number)object(released.body()).get("eventId")).longValue();assertEquals(200,post(c,catalogPath(),catalogCommand("WITHDRAW",event,UUID.randomUUID().toString(),false),true).statusCode());assertEquals("0",scalar("SELECT COUNT(*) FROM graph_catalog WHERE is_public=1"));assertEquals("2",scalar("SELECT COUNT(*) FROM review_event WHERE action LIKE 'CATALOG_%' AND actor_user_id IS NOT NULL"));}finally{resetCatalog();}
 }
 @Test void catalogRequiresRealAccountLicenseAndExplicitNameConfirmation()throws Exception{
  resetCatalog();try{var c=admin();assertEquals(422,post(c,catalogPath(),catalogCommand("RELEASE",0,UUID.randomUUID().toString(),false),true).statusCode());sql("UPDATE scientific_source_version SET license_state='UNKNOWN'");assertEquals(422,post(c,catalogPath(),catalogCommand("RELEASE",0,UUID.randomUUID().toString(),true),true).statusCode());assertEquals("0",scalar("SELECT COUNT(*) FROM review_event WHERE action LIKE 'CATALOG_%'"));assertEquals(401,get(client(),"/api/v1/admin/science/catalog").statusCode());assertEquals(403,post(c,catalogPath(),catalogCommand("RELEASE",0,UUID.randomUUID().toString(),true),false).statusCode());}finally{resetCatalog();}
 }
 @Test void concurrentCatalogReleaseHasOneWinner()throws Exception{
  resetCatalog();var pool=Executors.newFixedThreadPool(2);try{var c=admin();String a=catalogCommand("RELEASE",0,UUID.randomUUID().toString(),true),b=catalogCommand("RELEASE",0,UUID.randomUUID().toString(),true);var results=pool.invokeAll(List.of((Callable<Integer>)()->post(c,catalogPath(),a,true).statusCode(),()->post(c,catalogPath(),b,true).statusCode()));var statuses=new ArrayList<Integer>();for(var result:results)statuses.add(result.get());Collections.sort(statuses);assertEquals(List.of(200,409),statuses);}finally{pool.shutdownNow();resetCatalog();}
 }
 @Test void catalogLedgerFailureRollsBackTaxonomyAndVisibility()throws Exception{
  resetCatalog();sql("ALTER TABLE review_event ADD CONSTRAINT phase7_fail_catalog CHECK(action NOT LIKE 'CATALOG_%')");try{var c=admin();assertEquals(503,post(c,catalogPath(),catalogCommand("RELEASE",0,UUID.randomUUID().toString(),true),true).statusCode());assertEquals("RESEARCH_PREVIEW",scalar("SELECT visibility_state FROM plant WHERE id=1"));assertEquals("VERIFIED_FROM_PRINTED_HEADING",scalar("SELECT t.identity_state FROM taxon t JOIN plant p ON p.taxon_id=t.id WHERE p.id=1"));}finally{sql("ALTER TABLE review_event DROP CHECK phase7_fail_catalog");resetCatalog();}
 }
 @Test void catalogEvidenceVersionAndReplayContentAreChecked()throws Exception{
  resetCatalog();try{var c=admin();String body=catalogCommand("RELEASE",0,UUID.randomUUID().toString(),true);String changed=body.replace("Cycas revoluta Thunb.","Not the reviewed plant");assertEquals(409,post(c,catalogPath(),changed,true).statusCode());assertEquals(200,post(c,catalogPath(),body,true).statusCode());assertEquals(409,post(c,catalogPath(),body.replace("Isolated technical fixture only","A different technical fixture"),true).statusCode());assertEquals(404,get(c,"/api/v1/admin/science/catalog/999999").statusCode());assertEquals(400,get(c,"/api/v1/admin/science/catalog/-1").statusCode());}finally{resetCatalog();}
 }

 @Test void authorizedMetadataDoesNotVerifyIdentityLicenseOrScience()throws Exception{
  resetCatalog();try{
   String sha=scalar("SELECT a.sha256 FROM plant p JOIN taxon t ON t.id=p.taxon_id JOIN evidence e ON e.id=t.evidence_id JOIN source_asset a ON a.id=e.source_asset_id WHERE p.id=1");
   sql("UPDATE scientific_source_version SET license_state='UNKNOWN'");
   sql("UPDATE plant SET visibility_state='PUBLIC' WHERE id=1");
   String insert="INSERT INTO review_event(event_key,entity_type,entity_key,action,resulting_state,actor_user_id,happened_at,note,raw_payload) SELECT SHA2('ISOLATED_METADATA_RELEASE',256),'PLANT',record_key,'CATALOG_METADATA_RELEASE','METADATA_ONLY',NULL,CURRENT_TIMESTAMP(6),'ISOLATED METADATA TEST, NOT HUMAN REVIEW',JSON_OBJECT('scope','CATALOG_METADATA_ONLY','source_sha256','"+sha+"','scientific_name','Cycas revoluta Thunb.') FROM plant WHERE id=1";
   sql(insert);
   assertEquals("1",scalar("SELECT is_public FROM graph_catalog WHERE id=1"));
   var response=get(client(),"/api/v1/plants/1");assertEquals(200,response.statusCode(),response.body());assertTrue(response.body().contains("METADATA_ONLY"));assertFalse(response.body().contains("/Users/"));
   assertEquals("VERIFIED_FROM_PRINTED_HEADING",scalar("SELECT t.identity_state FROM taxon t JOIN plant p ON p.taxon_id=t.id WHERE p.id=1"));
   assertEquals("0",scalar("SELECT COUNT(*) FROM published_scientific_fact"));assertEquals("0",scalar("SELECT COUNT(*) FROM scientific_source_version WHERE license_state<>'UNKNOWN'"));
   var wb=get(client(),"/api/v1/workbench/plants/1");assertEquals(200,wb.statusCode(),wb.body());assertTrue(wb.body().contains("SOURCE_REPORTED"));
   assertEquals(200,get(client(),"/api/v1/graph/nodes/plant:1").statusCode());
   sql("UPDATE review_event SET raw_payload=JSON_SET(raw_payload,'$.source_sha256',REPEAT('0',64)) WHERE event_key=SHA2('ISOLATED_METADATA_RELEASE',256)");
   assertEquals(404,get(client(),"/api/v1/plants/1").statusCode());
   sql("UPDATE review_event SET raw_payload=JSON_SET(raw_payload,'$.source_sha256','"+sha+"') WHERE event_key=SHA2('ISOLATED_METADATA_RELEASE',256)");
   var admin=admin();long version=Long.parseLong(scalar("SELECT MAX(id) FROM review_event WHERE entity_type='PLANT'"));
   assertEquals(200,post(admin,catalogPath(),catalogCommand("WITHDRAW",version,UUID.randomUUID().toString(),false),true).statusCode());assertEquals(404,get(client(),"/api/v1/plants/1").statusCode());
  }finally{resetCatalog();}
 }

}
