package org.viridexa.api.agent;

import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.Files;
import java.sql.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.*;

/** End-to-end checks against the explicitly isolated governance test schema. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentMySqlIT {
    private static final String EMAIL="automated-isolated-fixture@example.invalid";
    private static final String RUNTIME_DIR;
    static {
        try { RUNTIME_DIR=Files.createTempDirectory("viridexa-agent-it-").toString(); }
        catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    @LocalServerPort int port;
    @MockitoBean ModelProvider model;
    static String env(String key){String value=System.getenv(key);if(value==null||value.isBlank())throw new IllegalStateException("Missing isolated test environment "+key);return value;}
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry r){
        if(!env("GOVERNANCE_TEST_SCHEMA").startsWith("viridexa_governance_test_"))throw new IllegalStateException("Production schema forbidden");
        r.add("spring.datasource.url",()->env("GOVERNANCE_TEST_DB_URL"));r.add("spring.datasource.username",()->env("GOVERNANCE_TEST_RO"));r.add("spring.datasource.password",()->env("GOVERNANCE_TEST_PASSWORD"));
        r.add("spring.flyway.user",()->env("GOVERNANCE_TEST_MIGRATOR"));r.add("spring.flyway.password",()->env("GOVERNANCE_TEST_PASSWORD"));
        r.add("viridexa.review.username",()->env("GOVERNANCE_TEST_WRITER"));r.add("viridexa.review.password",()->env("GOVERNANCE_TEST_PASSWORD"));r.add("viridexa.protected-root",()->env("VIRIDEXA_PROTECTED_ROOT"));
        r.add("viridexa.accounts.username",()->env("GOVERNANCE_TEST_ACCOUNT"));r.add("viridexa.accounts.password",()->env("GOVERNANCE_TEST_PASSWORD"));
        r.add("viridexa.agent.runtime-directory",()->RUNTIME_DIR);r.add("viridexa.agent.user-daily-requests",()->100);r.add("viridexa.agent.global-daily-requests",()->500);
    }
    Connection connection()throws Exception{return DriverManager.getConnection(env("GOVERNANCE_TEST_DB_URL"),env("GOVERNANCE_TEST_MIGRATOR"),env("GOVERNANCE_TEST_PASSWORD"));}
    String scalar(String query)throws Exception{try(var c=connection();var s=c.createStatement();var rs=s.executeQuery(query)){assertTrue(rs.next());return rs.getString(1);}}
    void sql(String query)throws Exception{try(var c=connection();var s=c.createStatement()){s.execute(query);}}
    String base(){return "http://127.0.0.1:"+port;}
    HttpClient client(){return HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(2)).build();}
    HttpResponse<String> get(HttpClient c,String path)throws Exception{return c.send(HttpRequest.newBuilder(URI.create(base()+path)).GET().build(),HttpResponse.BodyHandlers.ofString());}
    Map<String,Object> object(String text){return JsonParserFactory.getJsonParser().parseMap(text);}
    String csrf(HttpClient c)throws Exception{return (String)object(get(c,"/api/v1/csrf").body()).get("token");}
    HttpResponse<String> post(HttpClient c,String path,String body,boolean token)throws Exception{var b=HttpRequest.newBuilder(URI.create(base()+path)).header("Content-Type","application/json");if(token)b.header("X-CSRF-TOKEN",csrf(c));return c.send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());}
    HttpResponse<String> start(HttpClient c,String question,boolean csrf)throws Exception{return post(c,"/api/v1/agent/runs","{\"question\":"+quote(question)+"}",csrf);}
    HttpClient admin()throws Exception{var c=client();assertEquals(200,post(c,"/api/v1/admin/session","{\"username\":"+quote(EMAIL)+",\"password\":"+quote(env("GOVERNANCE_FIXTURE_PASSWORD"))+"}",true).statusCode());return c;}
    HttpClient member()throws Exception{var c=client();assertEquals(200,post(c,"/api/v1/account/login","{\"email\":\"researcher-fixture@example.invalid\",\"password\":"+quote(env("GOVERNANCE_FIXTURE_PASSWORD"))+"}",true).statusCode());return c;}
    String quote(String s){return "\""+s.replace("\\","\\\\").replace("\"","\\\"")+"\"";}
    Map<String,Object> await(HttpClient c,String id)throws Exception{
        long end=System.nanoTime()+Duration.ofSeconds(30).toNanos();Map<String,Object> run;
        do {run=object(get(c,"/api/v1/agent/runs/"+id).body());String status=(String)run.get("status");if(Set.of("COMPLETED","DEGRADED","FAILED","TIMED_OUT","CANCELLED").contains(status))return run;Thread.sleep(100);}while(System.nanoTime()<end);
        fail("Agent run did not finish within 30 seconds");return Map.of();
    }
    @BeforeEach void resetReviewState()throws Exception{reset(model);when(model.configured()).thenReturn(false);sql("DELETE FROM scientific_decision");sql("DELETE FROM scientific_review_state");sql("DELETE FROM source_license_event");sql("DELETE FROM source_governance");sql("UPDATE scientific_source_version SET license_state='UNKNOWN'");}

    @Test void sourceQuestionReturnsOnlyActualMetadataWithHashAndGraphHref()throws Exception{
        var c=member();var started=start(c,"What public data sources are available?",true);assertEquals(202,started.statusCode());Map<String,Object> initial=object(started.body());Map<String,Object> run=await(c,(String)initial.get("id"));
        assertEquals("DEGRADED",run.get("status"));Map<?,?> answer=(Map<?,?>)run.get("answer");List<?> claims=(List<?>)answer.get("claims");assertFalse(claims.isEmpty());
        String actualHash=scalar("SELECT sha256 FROM scientific_source_version ORDER BY id LIMIT 1");boolean found=false;
        for(Object item:claims){Map<?,?> claim=(Map<?,?>)item;String href=(String)claim.get("href");if(href==null||!href.startsWith("/api/v1/graph/nodes/"))continue;
            var graph=get(c,href);if(graph.statusCode()!=200)continue;Map<String,Object> detail=object(graph.body());String publicHash=findHash(detail);if(actualHash.equals(publicHash)){assertTrue(href.contains((String)claim.get("graphNodeId")));found=true;break;}}
        assertTrue(found,"A returned metadata claim should bind to an actual graph node and registered source SHA-256");
    }
    @Test void ownerIsolationAndCsrfProtectRunOperations()throws Exception{
        var owner=member();var response=start(owner,"What public sources are available?",true);assertEquals(202,response.statusCode());String id=(String)object(response.body()).get("id");
        assertEquals(401,get(client(),"/api/v1/agent/runs/"+id).statusCode());
        assertEquals(401,start(client(),"sources",true).statusCode());
        var other=admin();assertEquals(404,get(other,"/api/v1/agent/runs/"+id).statusCode());assertEquals(404,post(other,"/api/v1/agent/runs/"+id+"/cancel","{}",true).statusCode());
    }
    @Test void switchingAccountDoesNotInheritPreviousResearchRuns()throws Exception{
        var c=member();var response=start(c,"What sources exist?",true);assertEquals(202,response.statusCode());String id=(String)object(response.body()).get("id");await(c,id);
        assertEquals(200,post(c,"/api/v1/account/login","{\"email\":"+quote(EMAIL)+",\"password\":"+quote(env("GOVERNANCE_FIXTURE_PASSWORD"))+"}",true).statusCode());
        assertEquals(404,get(c,"/api/v1/agent/runs/"+id).statusCode());
    }
    @Test void csrfIsRequiredToStartAgentRun()throws Exception{
        assertEquals(403,start(client(),"What public sources are available?",false).statusCode());
    }
    @Test void pendingCandidateIdentifiersNeverBecomePublicClaimsEvenForAdmin()throws Exception{
        String pending=scalar("SELECT id FROM scientific_candidate WHERE normalization_state='CANDIDATE' ORDER BY id LIMIT 1");var admin=admin();var response=start(admin,"Please explain scientific candidate "+pending,true);assertEquals(202,response.statusCode());Map<String,Object> run=await(admin,(String)object(response.body()).get("id"));
        Map<?,?> answer=(Map<?,?>)run.get("answer");for(Object claim:(List<?>)answer.get("claims")){Map<?,?> e=(Map<?,?>)claim;assertNotEquals(pending,e.get("id"));assertFalse(e.toString().contains(pending));assertNotEquals("CANDIDATE",e.get("publicationState"));}
    }
    @Test void oilQuestionUsesSamplesGraphAndLineageWithoutPublishingPendingFacts()throws Exception{
        var c=member();var response=start(c,"Compare oil samples and their sources",true);assertEquals(202,response.statusCode());Map<String,Object> run=await(c,(String)object(response.body()).get("id"));
        List<?> trace=(List<?>)run.get("trace");Set<String> tools=new HashSet<>();for(Object t:trace)tools.add((String)((Map<?,?>)t).get("tool"));
        assertTrue(tools.containsAll(Set.of("searchPlants","getResearchSamples","getGraphNeighbors","getEvidenceLineage")),tools.toString());
        Map<?,?> answer=(Map<?,?>)run.get("answer");for(Object claim:(List<?>)answer.get("claims"))assertNotEquals("CANDIDATE",((Map<?,?>)claim).get("publicationState"));
    }
    @Test void mockedPlannerCallsRealGraphAndLineageServicesThenSelectsActualEvidence()throws Exception{
        String source=scalar("SELECT s.id FROM scientific_source_version s JOIN source_asset a ON a.sha256=s.sha256 JOIN graph_public_edge e ON (e.from_id=CONCAT('source:',s.id) OR e.to_id=CONCAT('source:',s.id)) WHERE e.relation_type='SOURCE_FILE' AND e.publication_state='METADATA_ONLY' ORDER BY s.id LIMIT 1");
        String sourceNode="source:"+source,edge=scalar("SELECT e.id FROM graph_public_edge e WHERE (e.from_id='"+sourceNode+"' OR e.to_id='"+sourceNode+"') AND e.relation_type='SOURCE_FILE' AND e.publication_state='METADATA_ONLY' ORDER BY e.id LIMIT 1");
        when(model.configured()).thenReturn(true);when(model.modelName()).thenReturn("isolated-mock-not-real");
        var firstCall=new AgentContract.ToolCall("it-call-1","getGraphNeighbors",Map.of("nodeId",sourceNode,"limit",5));
        var assistant=Map.<String,Object>of("role","assistant","tool_calls",List.of(Map.of("id","it-call-1","type","function","function",Map.of("name","getGraphNeighbors","arguments","{\"nodeId\":\""+sourceNode+"\",\"limit\":5}"))));
        var lineageCall=new AgentContract.ToolCall("it-call-2","getEvidenceLineage",Map.of("recordId",sourceNode));
        var first=new AgentContract.ModelTurn(assistant,List.of(firstCall,lineageCall),List.of(),12L,4L);
        var second=new AgentContract.ModelTurn(Map.of("role","assistant","content","{\"selectedEvidenceIds\":[\""+sourceNode+"\",\""+edge+"\"]}"),List.of(),List.of(sourceNode,edge),9L,2L);
        when(model.complete(anyList(),anyList(),any())).thenReturn(first,second);
        var c=member();var started=start(c,"Show source metadata and its file provenance",true);assertEquals(202,started.statusCode());Map<String,Object> run=await(c,(String)object(started.body()).get("id"));
        assertEquals("MODEL_TOOL_CALLING",run.get("mode"));assertEquals("COMPLETED",run.get("status"),run.toString());Map<?,?> usage=(Map<?,?>)run.get("usage");assertEquals(2,((Number)usage.get("modelCalls")).intValue());assertEquals(2,((Number)usage.get("toolCalls")).intValue());assertEquals(21,((Number)usage.get("inputTokens")).longValue());assertEquals(6,((Number)usage.get("outputTokens")).longValue());assertEquals(true,usage.get("tokenUsageComplete"));assertNull(usage.get("estimatedCost"));
        List<?> trace=(List<?>)run.get("trace");assertEquals(List.of("getGraphNeighbors","getEvidenceLineage"),trace.stream().map(t->((Map<?,?>)t).get("tool")).toList());
        Map<?,?> answer=(Map<?,?>)run.get("answer");List<?> claims=(List<?>)answer.get("claims");assertTrue(claims.stream().anyMatch(v->sourceNode.equals(((Map<?,?>)v).get("id"))));assertTrue(claims.stream().anyMatch(v->edge.equals(((Map<?,?>)v).get("id"))));assertTrue(claims.stream().noneMatch(v->"PUBLISHED".equals(((Map<?,?>)v).get("category"))&&((Map<?,?>)v).get("fields") instanceof Map<?,?> f&&f.containsKey("value")));
        verify(model,times(2)).complete(anyList(),anyList(),any());
    }
    @Test void currentReadRemovesARealMeasurementAfterTechnicalRevocation()throws Exception{
        String candidate=scalar("SELECT o.candidate_id FROM scientific_lipid_observation o WHERE o.kind='OIL_CONTENT' ORDER BY o.candidate_id LIMIT 1");
        String sample=scalar("SELECT sample_id FROM scientific_lipid_observation WHERE candidate_id='"+candidate+"'");
        String source=scalar("SELECT source_id FROM scientific_candidate WHERE id='"+candidate+"'");
        sql("UPDATE scientific_source_version SET license_state='PERMITTED'");
        var reviewer=admin();String path="/api/v1/admin/science/candidates/"+candidate+"/decisions";
        String requestId=UUID.randomUUID().toString();String approved="{\"action\":\"APPROVED\",\"expectedVersion\":0,\"rationale\":\"Isolated technical fixture only; no human scientific review\",\"requestId\":"+quote(requestId)+"}";
        var decision=post(reviewer,path,approved,true);assertEquals(200,decision.statusCode(),decision.body());
        when(model.configured()).thenReturn(true);when(model.modelName()).thenReturn("isolated-mock-not-real");
        var sampleCall=new AgentContract.ToolCall("it-sample-call","getResearchSamples",Map.of("sampleId",sample,"limit",5));
        var assistant=Map.<String,Object>of("role","assistant","tool_calls",List.of(Map.of("id","it-sample-call","type","function","function",Map.of("name","getResearchSamples","arguments","{\"sampleId\":\""+sample+"\",\"limit\":5}"))));
        var first=new AgentContract.ModelTurn(assistant,List.of(sampleCall),List.of(),12L,4L);
        var second=new AgentContract.ModelTurn(Map.of("role","assistant","content","{\"selectedEvidenceIds\":[\""+candidate+"\"]}"),List.of(),List.of(candidate),8L,2L);
        when(model.complete(anyList(),anyList(),any())).thenReturn(first,second);
        var c=member();var started=start(c,"Show this oil measurement and its source",true);assertEquals(202,started.statusCode());String runId=(String)object(started.body()).get("id");Map<String,Object> before=await(c,runId);
        assertEquals("COMPLETED",before.get("status"),before.toString());Map<?,?> beforeAnswer=(Map<?,?>)before.get("answer");assertTrue(((List<?>)beforeAnswer.get("claims")).stream().anyMatch(v->candidate.equals(((Map<?,?>)v).get("id"))));
        String revoke="{\"action\":\"REVOKED\",\"expectedVersion\":1,\"rationale\":\"Isolated technical fixture only; no human scientific review\",\"requestId\":"+quote(UUID.randomUUID().toString())+"}";
        var revoked=post(reviewer,path,revoke,true);assertEquals(200,revoked.statusCode(),revoked.body());
        Map<String,Object> after=object(get(c,"/api/v1/agent/runs/"+runId).body());Map<?,?> afterAnswer=(Map<?,?>)after.get("answer");assertTrue(((List<?>)afterAnswer.get("claims")).isEmpty());assertEquals("INSUFFICIENT_EVIDENCE",afterAnswer.get("status"));assertTrue(((List<?>)afterAnswer.get("limitations")).stream().anyMatch(v->String.valueOf(v).contains("已撤回")));
    }
    private static String findHash(Object value){if(value instanceof Map<?,?> m){for(var e:m.entrySet()){if("sha256".equalsIgnoreCase(String.valueOf(e.getKey()))&&e.getValue() instanceof String s)return s;String nested=findHash(e.getValue());if(nested!=null)return nested;}}else if(value instanceof Iterable<?> values)for(Object item:values){String nested=findHash(item);if(nested!=null)return nested;}return null;}
}
