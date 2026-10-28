package org.viridexa.api.graph;

import java.net.*;
import java.net.http.*;
import java.sql.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.json.JsonParserFactory;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit isolated-schema graph integration checks; never writes fixtures to viridexa. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class GraphMySqlIT {
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
 List<Map<String,Object>> rows(String query)throws Exception{var result=new ArrayList<Map<String,Object>>();try(var c=connection();var statement=c.createStatement();var rs=statement.executeQuery(query)){var meta=rs.getMetaData();while(rs.next()){var row=new LinkedHashMap<String,Object>();for(int i=1;i<=meta.getColumnCount();i++)row.put(meta.getColumnLabel(i),rs.getObject(i));result.add(row);}}return result;}
 void restoreAssets(List<Map<String,Object>> assets)throws Exception{try(var c=connection();var insert=c.prepareStatement("INSERT INTO source_asset(asset_key,relative_path,sha256,size_bytes,media_type,asset_kind,ingest_state,metadata) VALUES(?,?,?,?,?,?,?,?)")){for(var a:assets){insert.setObject(1,a.get("asset_key"));insert.setObject(2,a.get("relative_path"));insert.setObject(3,a.get("sha256"));insert.setObject(4,a.get("size_bytes"));insert.setObject(5,a.get("media_type"));insert.setObject(6,a.get("asset_kind"));insert.setObject(7,a.get("ingest_state"));insert.setObject(8,a.get("metadata"));insert.addBatch();}insert.executeBatch();}}
 String candidate(String kind)throws Exception{return scalar("SELECT candidate_id FROM scientific_lipid_observation WHERE kind='"+kind+"' AND value_numeric IS NOT NULL ORDER BY candidate_id LIMIT 1");}
 String digest(String text)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}
 String base(){return "http://127.0.0.1:"+port;}
 HttpClient client(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).build();}
 HttpResponse<String> get(HttpClient c,String path)throws Exception{return c.send(HttpRequest.newBuilder(URI.create(base()+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
 Map<String,Object> object(String text){return JsonParserFactory.getJsonParser().parseMap(text);}
 long nodeCount(Map<String,Object> meta,String type){Map<?,?> counts=(Map<?,?>)meta.get("nodeCounts");Object count=counts.get(type);return count instanceof Number n?n.longValue():0;}
 String csrf(HttpClient c)throws Exception{return (String)object(get(c,"/api/v1/csrf").body()).get("token");}
 HttpResponse<String> post(HttpClient c,String path,String body,boolean token)throws Exception{
  var builder=HttpRequest.newBuilder(URI.create(base()+path)).header("Content-Type","application/json");if(token)builder.header("X-CSRF-TOKEN",csrf(c));
  return c.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
 }
 HttpClient admin()throws Exception{var c=client();assertEquals(200,post(c,"/api/v1/admin/session","{\"username\":\""+EMAIL+"\",\"password\":\""+env("GOVERNANCE_FIXTURE_PASSWORD")+"\"}",true).statusCode());return c;}
 String command(String action,int version,String request){return "{\"action\":\""+action+"\",\"expectedVersion\":"+version+",\"rationale\":\"Isolated graph fixture only; no human scientific review\",\"requestId\":\""+request+"\"}";}
 String decisionPath(String id){return "/api/v1/admin/science/candidates/"+id+"/decisions";}
 @BeforeEach void resetAndAddMetadataOnlyFileFixture()throws Exception{
  sql("DELETE FROM scientific_decision");sql("DELETE FROM scientific_review_state");sql("DELETE FROM source_license_event");sql("DELETE FROM source_governance");sql("UPDATE scientific_source_version SET license_state='PERMITTED'");
  String source=scalar("SELECT id FROM scientific_source_version ORDER BY id LIMIT 1");String hash=scalar("SELECT sha256 FROM scientific_source_version WHERE id='"+source+"'");String assetKey=digest("graph-it-source-file:"+source);
  sql("INSERT INTO source_asset(asset_key,relative_path,sha256,size_bytes,media_type,asset_kind,ingest_state) SELECT '"+assetKey+"','isolated-fixture/catalog-metadata.bin','"+hash+"',0,'application/octet-stream','DATASET_ARCHIVE','INDEXED' WHERE NOT EXISTS(SELECT 1 FROM source_asset WHERE asset_key='"+assetKey+"')");
  String taxonA=digest("graph-it-taxonomy-a"),taxonB=digest("graph-it-taxonomy-b");
  sql("INSERT INTO taxon(taxon_key,taxon_rank,canonical_name,identity_state) SELECT '"+taxonA+"','GENUS','isolated graph fixture A','NEEDS_REVIEW' WHERE NOT EXISTS(SELECT 1 FROM taxon WHERE taxon_key='"+taxonA+"')");
  sql("INSERT INTO taxon(taxon_key,taxon_rank,canonical_name,identity_state) SELECT '"+taxonB+"','GENUS','isolated graph fixture B','NEEDS_REVIEW' WHERE NOT EXISTS(SELECT 1 FROM taxon WHERE taxon_key='"+taxonB+"')");
  String idA=scalar("SELECT id FROM taxon WHERE taxon_key='"+taxonA+"'"),idB=scalar("SELECT id FROM taxon WHERE taxon_key='"+taxonB+"'");
  sql("UPDATE taxon SET parent_id="+idB+" WHERE id="+idA);sql("UPDATE taxon SET parent_id="+idA+" WHERE id="+idB);
 }
 @Test void publicGraphShowsSourceFileProvenanceWithoutFileContents()throws Exception{
  var anon=client();var meta=get(anon,"/api/v1/graph/meta");assertEquals(200,meta.statusCode());assertTrue(meta.body().contains("SOURCE_FILE"));
  String source=scalar("SELECT id FROM scientific_source_version ORDER BY id LIMIT 1"),file=scalar("SELECT id FROM source_asset WHERE relative_path='isolated-fixture/catalog-metadata.bin'");
  var neighbor=get(anon,"/api/v1/graph/nodes/source:"+source+"/neighbors?relationType=SOURCE_FILE");assertEquals(200,neighbor.statusCode());assertTrue(neighbor.body().contains("file:"+file));assertTrue(neighbor.body().contains("HASH_MATCH"));
  assertFalse(neighbor.body().contains("isolated-fixture/catalog-metadata.bin"));assertFalse(neighbor.body().contains("raw_payload"));assertFalse(neighbor.body().contains("/Users/"));
 }
 @Test void publicSamplesExposeOnlyPublishedCandidateEdgesAndRetractImmediately()throws Exception{
  String id=candidate("OIL_CONTENT"),sample=scalar("SELECT sample_id FROM scientific_lipid_observation WHERE candidate_id='"+id+"'"),source=scalar("SELECT source_id FROM scientific_candidate WHERE id='"+id+"'");var anon=client();
  assertEquals(404,get(anon,"/api/v1/graph/nodes/candidate:"+id).statusCode());assertEquals(401,get(anon,"/api/v1/admin/graph/nodes/candidate:"+id).statusCode());
  var admin=admin();var approval=post(admin,decisionPath(id),command("APPROVED",0,UUID.randomUUID().toString()),true);assertEquals(200,approval.statusCode(),approval.body());
  Map<String,Object> publishedMeta=object(get(anon,"/api/v1/graph/meta").body());assertEquals(1,nodeCount(publishedMeta,"MEASUREMENT"));assertEquals(1L,((Number)publishedMeta.get("publishedFactCount")).longValue());
  var detail=get(anon,"/api/v1/graph/nodes/candidate:"+id);assertEquals(200,detail.statusCode());assertTrue(detail.body().contains("PUBLISHED"));assertFalse(detail.body().contains("raw_payload"));assertFalse(detail.body().contains("rawValue"));
  var sourcePage=get(anon,"/api/v1/graph/nodes/source:"+source+"/neighbors?relationType=SOURCED_FROM&nodeType=MEASUREMENT");assertEquals(200,sourcePage.statusCode());assertTrue(sourcePage.body().contains("candidate:"+id));assertEquals(1,((List<?>)object(sourcePage.body()).get("edges")).size());
  List<String> pending=rows("SELECT id FROM scientific_candidate WHERE source_id='"+source+"' AND pipeline='LIPID' AND normalization_state='CANDIDATE' AND id<>'"+id+"'").stream().map(r->r.get("id").toString()).toList();assertFalse(pending.isEmpty());for(String hidden:pending)assertFalse(sourcePage.body().contains("candidate:"+hidden));
  var neighbors=get(anon,"/api/v1/graph/nodes/sample:"+sample+"/neighbors?relationType=HAS_MEASUREMENT");assertEquals(200,neighbors.statusCode());assertTrue(neighbors.body().contains("candidate:"+id));
  var revoke=post(admin,decisionPath(id),command("REVOKED",1,UUID.randomUUID().toString()),true);assertEquals(200,revoke.statusCode(),revoke.body());assertEquals(404,get(anon,"/api/v1/graph/nodes/candidate:"+id).statusCode());Map<String,Object> revokedMeta=object(get(anon,"/api/v1/graph/meta").body());assertEquals(0,nodeCount(revokedMeta,"MEASUREMENT"));assertEquals(0L,((Number)revokedMeta.get("publishedFactCount")).longValue());var revokedSourcePage=get(anon,"/api/v1/graph/nodes/source:"+source+"/neighbors?relationType=SOURCED_FROM&nodeType=MEASUREMENT");assertEquals(200,revokedSourcePage.statusCode());assertTrue(((List<?>)object(revokedSourcePage.body()).get("edges")).isEmpty());
 }
 @Test void cursorCannotCrossViewOrSearchContextAndLimitsAreEnforced()throws Exception{
  var admin=admin();var first=get(admin,"/api/v1/admin/graph/search?limit=1");assertEquals(200,first.statusCode());Map<String,Object> body=object(first.body());
  assertEquals(true,body.get("hasMore"));String cursor=(String)body.get("nextCursor");assertEquals(400,get(admin,"/api/v1/admin/graph/search?q=different&limit=1&cursor="+URLEncoder.encode(cursor,StandardCharsets.UTF_8)).statusCode());assertEquals(400,get(client(),"/api/v1/graph/search?limit=1&cursor="+URLEncoder.encode(cursor,StandardCharsets.UTF_8)).statusCode());
  assertEquals(400,get(client(),"/api/v1/graph/search?limit=41").statusCode());
 }

 @Test void missingAndMalformedNodeIdsHaveDistinctSafeResponses()throws Exception{
  var anon=client();assertEquals(404,get(anon,"/api/v1/graph/nodes/plant:999999999").statusCode());assertEquals(400,get(anon,"/api/v1/graph/nodes/not-a-node").statusCode());
 }
 @Test void isolatedRealSourceHasNoNeighbors()throws Exception{
  String id=scalar("SELECT s.id FROM scientific_source_version s WHERE NOT EXISTS(SELECT 1 FROM graph_edge e WHERE (e.from_id=CONCAT('source:',s.id) OR e.to_id=CONCAT('source:',s.id)) AND e.relation_type<>'SOURCE_FILE') AND EXISTS(SELECT 1 FROM source_asset a WHERE a.sha256=s.sha256) AND NOT EXISTS(SELECT 1 FROM evidence ev JOIN source_asset a ON a.id=ev.source_asset_id WHERE a.sha256=s.sha256) AND NOT EXISTS(SELECT 1 FROM media_asset m JOIN source_asset a ON a.id=m.source_asset_id WHERE a.sha256=s.sha256) ORDER BY s.id LIMIT 1");
  String sha=scalar("SELECT sha256 FROM scientific_source_version WHERE id='"+id+"'");var assets=rows("SELECT asset_key,relative_path,sha256,size_bytes,media_type,asset_kind,ingest_state,metadata FROM source_asset WHERE sha256='"+sha+"'");assertFalse(assets.isEmpty());String source="source:"+id;
  sql("DELETE FROM source_asset WHERE sha256='"+sha+"'");try{var response=get(admin(),"/api/v1/admin/graph/nodes/"+source+"/neighbors");assertEquals(200,response.statusCode());Map<String,Object> page=object(response.body());assertEquals(1,((List<?>)page.get("nodes")).size());assertEquals(0,((List<?>)page.get("edges")).size());assertFalse((Boolean)page.get("hasMore"));}finally{restoreAssets(assets);}
 }
 @Test void cyclicTaxonomyFixturesRemainAdminOnlyAndOneHopBounded()throws Exception{
  String a=scalar("SELECT id FROM taxon WHERE canonical_name='isolated graph fixture A'"),b=scalar("SELECT id FROM taxon WHERE canonical_name='isolated graph fixture B'");var anon=client();assertEquals(404,get(anon,"/api/v1/graph/nodes/taxon:"+a).statusCode());
  var response=get(admin(),"/api/v1/admin/graph/nodes/taxon:"+a+"/neighbors?relationType=PARENT_TAXON");assertEquals(200,response.statusCode());Map<String,Object> page=object(response.body());assertEquals(2,((List<?>)page.get("nodes")).size());assertEquals(2,((List<?>)page.get("edges")).size());assertFalse(response.body().contains("plant:"));assertEquals(400,get(admin(),"/api/v1/admin/graph/nodes/taxon:"+a+"/neighbors?depth=2").statusCode());
 }
 @Test void sourceProvenanceFiltersAndNeighborCursorAreExact()throws Exception{
  String source=scalar("SELECT c.source_id FROM scientific_candidate c WHERE c.pipeline='LIPID' GROUP BY c.source_id HAVING COUNT(*)>=2 ORDER BY c.source_id LIMIT 1");var admin=admin();String url="/api/v1/admin/graph/nodes/source:"+source+"/neighbors?relationType=SOURCED_FROM&nodeType=MEASUREMENT&verificationState=PENDING&limit=1";
  var first=get(admin,url);assertEquals(200,first.statusCode());Map<String,Object> page1=object(first.body());assertEquals(true,page1.get("hasMore"));assertEquals(1,((List<?>)page1.get("edges")).size());assertEquals("SOURCED_FROM",((Map<?,?>)((List<?>)page1.get("edges")).getFirst()).get("relationType"));assertEquals(400,get(admin,url.replace("nodeType=MEASUREMENT","nodeType=MORPHOLOGY")+"&cursor="+URLEncoder.encode((String)page1.get("nextCursor"),StandardCharsets.UTF_8)).statusCode());
  assertEquals(0,((List<?>)object(get(admin,url+"&publicationState=PUBLISHED").body()).get("edges")).size());
  assertEquals(1,((List<?>)object(get(admin,url+"&publicationState=CANDIDATE").body()).get("edges")).size());
  assertEquals(400,get(admin,url+"&publicationState=CANDIDATE&cursor="+URLEncoder.encode((String)page1.get("nextCursor"),StandardCharsets.UTF_8)).statusCode());
  var second=get(admin,url+"&cursor="+URLEncoder.encode((String)page1.get("nextCursor"),StandardCharsets.UTF_8));assertEquals(200,second.statusCode());Map<?,?> edge1=(Map<?,?>)((List<?>)page1.get("edges")).getFirst(),edge2=(Map<?,?>)((List<?>)object(second.body()).get("edges")).getFirst();assertNotEquals(edge1.get("id"),edge2.get("id"));assertEquals(1,((List<?>)object(second.body()).get("edges")).size());
 }
 @Test void adminCandidatePreviewShowsCurrentReviewAndSampleContextOnlyToAdmin()throws Exception{
  String id=candidate("OIL_CONTENT");var anon=client();var admin=admin();var detail=get(admin,"/api/v1/admin/graph/nodes/candidate:"+id);assertEquals(200,detail.statusCode());assertTrue(detail.body().contains("rawValue"));assertTrue(detail.body().contains("normalizedValue"));assertTrue(detail.body().contains("sample_id"));assertTrue(detail.body().contains("PENDING"));assertFalse(detail.body().contains("automated-isolated-fixture@example.invalid"));
  assertEquals(404,get(anon,"/api/v1/graph/nodes/candidate:"+id).statusCode());assertFalse(get(anon,"/api/v1/graph/meta").body().contains(id));
 }
 @Test void morphologyProjectionEmitsSourceReportedTraitIdsAndEvidence()throws Exception{
  String id=scalar("SELECT c.id FROM scientific_candidate c JOIN graph_trait t ON t.candidate_id=c.id WHERE c.pipeline='MORPHOLOGY' ORDER BY c.id LIMIT 1");var admin=admin();var response=get(admin,"/api/v1/admin/graph/nodes/candidate:"+id+"/neighbors?relationType=HAS_TRAIT");assertEquals(200,response.statusCode());Map<String,Object> page=object(response.body());assertFalse(((List<?>)page.get("edges")).isEmpty());assertTrue(response.body().contains("trait:"+id+":"));assertTrue(response.body().contains("sourceTerm"));assertTrue(response.body().contains("sourceReference"));assertTrue(response.body().contains("sha256"));assertTrue(response.body().contains("PENDING"));assertTrue(response.body().contains("CANDIDATE"));
  assertEquals(404,get(client(),"/api/v1/graph/nodes/candidate:"+id).statusCode());
 }
 @Test void realCourseLedgerChainStaysAdminOnlyAndPending()throws Exception{
  String sample=scalar("SELECT sm.id FROM research_sample sm JOIN measurement m ON m.research_sample_id=sm.id WHERE sm.plant_id IS NOT NULL AND sm.evidence_id IS NOT NULL AND m.evidence_id IS NOT NULL ORDER BY sm.id LIMIT 1");
  String plant=scalar("SELECT plant_id FROM research_sample WHERE id="+sample),measurement=scalar("SELECT id FROM measurement WHERE research_sample_id="+sample+" ORDER BY id LIMIT 1");var admin=admin();var anon=client();
  String plantPageUrl="/api/v1/admin/graph/nodes/plant:"+plant+"/neighbors?relationType=HAS_RESEARCH_RECORD&nodeType=COURSE_SAMPLE";var plantPage=get(admin,plantPageUrl);assertEquals(200,plantPage.statusCode(),plantPage.body());assertTrue(plantPage.body().contains("course-sample:"+sample));assertTrue(plantPage.body().contains("HAS_RESEARCH_RECORD"));assertTrue(plantPage.body().contains("SOURCE_REPORTED"));assertTrue(plantPage.body().contains("CANDIDATE"));
  var coursePage=get(admin,"/api/v1/admin/graph/nodes/course-sample:"+sample+"/neighbors?relationType=HAS_MEASUREMENT&nodeType=COURSE_MEASUREMENT");assertEquals(200,coursePage.statusCode(),coursePage.body());assertTrue(coursePage.body().contains("course-measurement:"+measurement));assertTrue(coursePage.body().contains("rawValue"));assertTrue(coursePage.body().contains("numericValue"));assertTrue(coursePage.body().contains("sourceReference"));assertTrue(coursePage.body().contains("sha256"));assertTrue(coursePage.body().contains("SOURCE_REPORTED"));assertTrue(coursePage.body().contains("CANDIDATE"));
  assertEquals(404,get(anon,"/api/v1/graph/nodes/course-sample:"+sample).statusCode());assertEquals(404,get(anon,"/api/v1/graph/nodes/course-measurement:"+measurement).statusCode());Map<String,Object> publicMeta=object(get(anon,"/api/v1/graph/meta").body());assertEquals(0,nodeCount(publicMeta,"COURSE_SAMPLE"));assertEquals(0,nodeCount(publicMeta,"COURSE_MEASUREMENT"));
 }
}
