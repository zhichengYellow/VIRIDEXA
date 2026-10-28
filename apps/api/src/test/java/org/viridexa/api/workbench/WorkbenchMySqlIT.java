package org.viridexa.api.workbench;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/** End-to-end workbench checks against an explicitly isolated governance schema. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WorkbenchMySqlIT {
    private static final String ADMIN_EMAIL = "automated-isolated-fixture@example.invalid";
    private static final String RESEARCHER_EMAIL = "researcher-fixture@example.invalid";
    @LocalServerPort int port;

    static String env(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("Missing isolated test environment " + key);
        return value;
    }
    @DynamicPropertySource static void configuration(DynamicPropertyRegistry r) {
        if (!env("GOVERNANCE_TEST_SCHEMA").startsWith("viridexa_governance_test_")) throw new IllegalStateException("Production schema forbidden");
        r.add("spring.datasource.url", () -> env("GOVERNANCE_TEST_DB_URL"));
        r.add("spring.datasource.username", () -> env("GOVERNANCE_TEST_RO"));
        r.add("spring.datasource.password", () -> env("GOVERNANCE_TEST_PASSWORD"));
        r.add("spring.flyway.user", () -> env("GOVERNANCE_TEST_MIGRATOR"));
        r.add("spring.flyway.password", () -> env("GOVERNANCE_TEST_PASSWORD"));
        r.add("viridexa.review.username", () -> env("GOVERNANCE_TEST_WRITER"));
        r.add("viridexa.review.password", () -> env("GOVERNANCE_TEST_PASSWORD"));
        r.add("viridexa.accounts.username", () -> env("GOVERNANCE_TEST_ACCOUNT")); r.add("viridexa.accounts.password", () -> env("GOVERNANCE_TEST_PASSWORD"));
        r.add("viridexa.protected-root", () -> env("VIRIDEXA_PROTECTED_ROOT"));
    }
    Connection connection() throws Exception { return DriverManager.getConnection(env("GOVERNANCE_TEST_DB_URL"), env("GOVERNANCE_TEST_MIGRATOR"), env("GOVERNANCE_TEST_PASSWORD")); }
    void sql(String query) throws Exception { try (var c = connection(); var s = c.createStatement()) { s.execute(query); } }
    String scalar(String query) throws Exception { try (var c = connection(); var s = c.createStatement(); var rs = s.executeQuery(query)) { assertTrue(rs.next(), query); return rs.getString(1); } }
    String base() { return "http://127.0.0.1:" + port; }
    HttpClient client() { return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build(); }
    HttpResponse<String> get(HttpClient c, String path) throws Exception { return c.send(HttpRequest.newBuilder(URI.create(base() + path)).GET().build(), HttpResponse.BodyHandlers.ofString()); }
    Map<String,Object> object(String json) { return JsonParserFactory.getJsonParser().parseMap(json); }
    String quote(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""; }
    String csrf(HttpClient c) throws Exception { return String.valueOf(object(get(c, "/api/v1/csrf").body()).get("token")); }
    HttpResponse<String> post(HttpClient c, String path, String body, boolean withCsrf) throws Exception {
        var b = HttpRequest.newBuilder(URI.create(base() + path)).header("Content-Type", "application/json");
        if (withCsrf) b.header("X-CSRF-TOKEN", csrf(c));
        return c.send(b.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    HttpClient admin() throws Exception { var c=client(); assertEquals(200,post(c,"/api/v1/admin/session","{\"username\":"+quote(ADMIN_EMAIL)+",\"password\":"+quote(env("GOVERNANCE_FIXTURE_PASSWORD"))+"}",true).statusCode()); return c; }
    HttpClient login(String email) throws Exception {
        var c = client();
        assertEquals(200, post(c, "/api/v1/account/login", "{\"email\":" + quote(email) + ",\"password\":" + quote(env("GOVERNANCE_FIXTURE_PASSWORD")) + "}", true).statusCode());
        return c;
    }
    String command(String action, int version) {
        return "{\"action\":" + quote(action) + ",\"expectedVersion\":" + version + ",\"rationale\":\"Isolated technical fixture only; no human scientific review\",\"requestId\":" + quote(UUID.randomUUID().toString()) + "}";
    }
    String decision(String id, String action, int version) throws Exception {
        var response = post(admin(), "/api/v1/admin/science/candidates/" + id + "/decisions", command(action, version), true);
        assertEquals(200, response.statusCode(), response.body());
        return response.body();
    }
    String candidate(String kind) throws Exception { return scalar("SELECT candidate_id FROM scientific_lipid_observation WHERE kind='" + kind + "' ORDER BY candidate_id LIMIT 1"); }
    String point(String candidate) { return "candidate:" + candidate; }
    void permitCandidateSources(String candidate) throws Exception {
        sql("UPDATE scientific_source_version s JOIN (SELECT source_id AS id FROM scientific_candidate WHERE id='" + candidate + "' UNION SELECT document_source_id AS id FROM scientific_evidence_link WHERE candidate_id='" + candidate + "') linked ON linked.id=s.id SET s.license_state='PERMITTED'");
    }

    @BeforeEach void resetReviewState() throws Exception {
        sql("DELETE FROM scientific_decision");
        sql("DELETE FROM scientific_review_state");
        sql("DELETE FROM source_license_event");
        sql("DELETE FROM source_governance");
        sql("UPDATE scientific_source_version SET license_state='UNKNOWN'");
    }

    @Test void publicViewHasNoFactsUntilApprovedAndAdminCanSeeCandidates() throws Exception {
        String id = candidate("OIL_CONTENT");
        var anon = client(); var reviewer = admin();
        var publicOverview = get(anon, "/api/v1/workbench/overview");
        assertEquals(200, publicOverview.statusCode());
        assertEquals(0L, ((Number) object(publicOverview.body()).get("publishedFactCount")).longValue());
        assertEquals(401, get(anon, "/api/v1/admin/workbench/overview").statusCode());
        var adminOverview = get(reviewer, "/api/v1/admin/workbench/overview");
        assertEquals(200, adminOverview.statusCode(), adminOverview.body());
        assertTrue(((Number) object(adminOverview.body()).get("visibleSamples")).longValue() > 0);
        var unlinked = get(reviewer, "/api/v1/admin/workbench/samples?unlinked=true&limit=10");
        assertEquals(200, unlinked.statusCode(), unlinked.body());
        assertTrue(unlinked.body().contains("UNLINKED"));
        assertEquals(401, get(anon, "/api/v1/workbench/samples").statusCode());
        var member = login(RESEARCHER_EMAIL);
        var hidden = get(member, "/api/v1/workbench/points?sampleIds=sample:" + scalar("SELECT sample_id FROM scientific_lipid_observation WHERE candidate_id='" + id + "'") + "&kind=OIL_CONTENT");
        assertEquals(404, hidden.statusCode(), hidden.body());
        var candidatePage = get(reviewer, "/api/v1/admin/workbench/points?sampleIds=sample:" + scalar("SELECT sample_id FROM scientific_lipid_observation WHERE candidate_id='" + id + "'") + "&kind=OIL_CONTENT");
        assertEquals(200, candidatePage.statusCode(), candidatePage.body()); assertTrue(candidatePage.body().contains(id));
        assertTrue(candidatePage.body().contains("CANDIDATE"));
        permitCandidateSources(id);
        decision(id, "APPROVED", 0);
        var publicPoints = get(member, "/api/v1/workbench/points?sampleIds=sample:" + scalar("SELECT sample_id FROM scientific_lipid_observation WHERE candidate_id='" + id + "'") + "&kind=OIL_CONTENT");
        assertEquals(200, publicPoints.statusCode(), publicPoints.body()); assertTrue(publicPoints.body().contains(id)); assertTrue(publicPoints.body().contains("PUBLISHED"));
        assertFalse(publicPoints.body().contains("raw_payload"));
    }

    @Test void paginationCursorIsBoundToScopeAndQueryAndLimitsAreValidated() throws Exception {
        String oil = candidate("OIL_CONTENT"), fame = candidate("FAME_COMPOSITION");
        String oilSample = scalar("SELECT sample_id FROM scientific_lipid_observation WHERE candidate_id='" + oil + "'");
        String fameSample = scalar("SELECT sample_id FROM scientific_lipid_observation WHERE candidate_id='" + fame + "'");
        assertNotEquals(oilSample, fameSample, "pagination fixture must contain two distinct samples");
        for (String id : List.of(oil, fame)) {
            permitCandidateSources(id); decision(id, "APPROVED", 0);
        }
        var c = login(RESEARCHER_EMAIL);
        var first = get(c, "/api/v1/workbench/samples?limit=1"); assertEquals(200, first.statusCode(), first.body());
        Map<String,Object> page = object(first.body());
        assertEquals(Boolean.TRUE, page.get("hasMore"));
        var firstItems = (List<Map<String,Object>>) page.get("items");
        assertEquals(1, firstItems.size());
        String firstId = String.valueOf(firstItems.getFirst().get("id"));
        String cursor = URLEncoder.encode(String.valueOf(page.get("nextCursor")), StandardCharsets.UTF_8);
        assertEquals(400, get(c, "/api/v1/workbench/samples?q=not-the-same&limit=1&cursor=" + cursor).statusCode());
        assertEquals(400, get(admin(), "/api/v1/admin/workbench/samples?limit=1&cursor=" + cursor).statusCode());
        var second = get(c, "/api/v1/workbench/samples?limit=1&cursor=" + cursor);
        assertEquals(200, second.statusCode(), second.body());
        Map<String,Object> secondPage = object(second.body());
        var secondItems = (List<Map<String,Object>>) secondPage.get("items");
        assertEquals(1, secondItems.size());
        assertNotEquals(firstId, secondItems.getFirst().get("id"), "cursor page must not repeat the first sample");
        assertEquals(Boolean.FALSE, secondPage.get("hasMore"));
        assertEquals(400, get(c, "/api/v1/workbench/samples?limit=999").statusCode());
        assertEquals(400, get(c, "/api/v1/workbench/points?sampleIds=sample:bad&limit=1").statusCode());
    }

    @Test void csrfRoleAndSourceSafetyAreEnforced() throws Exception {
        String id = candidate("OIL_CONTENT"); var reviewer = admin(); var anon = client();
        assertEquals(403, post(anon, "/api/v1/workbench/compare", "{\"pointIds\":[\"candidate:" + id + "\",\"candidate:" + id + "\"]}", false).statusCode());
        // Researcher credentials cannot establish the ADMIN-only session in the first place.
        assertEquals(403, post(anon, "/api/v1/admin/session", "{\"username\":" + quote(RESEARCHER_EMAIL) + ",\"password\":" + quote(env("GOVERNANCE_FIXTURE_PASSWORD")) + "}", true).statusCode());
        assertEquals(401, get(anon, "/api/v1/admin/workbench/samples").statusCode());
        var ev = get(reviewer, "/api/v1/admin/workbench/points/candidate:" + id + "/evidence");
        assertEquals(200, ev.statusCode(), ev.body());
        for (String forbidden : List.of("raw_payload", "reviewer_id", "GOVERNANCE_TEST_PASSWORD", "/Users/", "/home/")) assertFalse(ev.body().contains(forbidden), forbidden);
        Map<?,?> evidence = object(ev.body());
        Map<?,?> evidencePoint = (Map<?,?>) evidence.get("point");
        List<Map<?,?>> refs = (List<Map<?,?>>) evidence.get("sources");
        assertFalse(refs.isEmpty());
        assertEquals(evidencePoint.get("sourceId"), refs.getFirst().get("sourceId"));
        assertTrue(String.valueOf(refs.getFirst().get("sha256")).matches("[0-9a-f]{64}"));
        assertEquals("METADATA_ONLY_NO_PUBLIC_ORIGINAL", evidence.get("availability"));
        String documentSource=scalar("SELECT document_source_id FROM scientific_evidence_link WHERE candidate_id='"+id+"' ORDER BY id LIMIT 1");
        assertTrue(refs.stream().anyMatch(ref->documentSource.equals(ref.get("sourceId"))),"Actual original-document relationship must be present");
        assertTrue(ev.body().contains("SUPPORTED_BY"));assertTrue(ev.body().contains("pdf_page"));
    }

    @Test void comparisonRejectsMissingPublicPointsAndKeepsUnverifiedCandidateIdentityInsufficient() throws Exception {
        String oil = candidate("OIL_CONTENT"), fame = candidate("FAME_COMPOSITION");
        var anon = login(RESEARCHER_EMAIL);
        String body = "{\"pointIds\":[" + quote(point(oil)) + "," + quote(point(fame)) + "]}";
        assertEquals(404, post(anon, "/api/v1/workbench/compare", body, true).statusCode());
        var adminResponse = post(admin(), "/api/v1/admin/workbench/compare", body, true);
        assertEquals(200, adminResponse.statusCode(), adminResponse.body());
        assertTrue(adminResponse.body().contains("INSUFFICIENT"));
        assertTrue(adminResponse.body().contains("不构成人工科学审核"));
    }

    @Test void agentContextCannotUseHiddenSamplesOrUnknownAuthorityFields() throws Exception {
        String hiddenSample = scalar("SELECT sample_id FROM scientific_lipid_observation WHERE candidate_id='" + candidate("OIL_CONTENT") + "'");
        var anon = login(RESEARCHER_EMAIL);
        String hidden = "{\"question\":\"inspect sample\",\"context\":{\"sampleIds\":[\"sample:" + hiddenSample + "\"],\"authority\":\"ADMIN\"}}";
        assertEquals(404, post(anon, "/api/v1/agent/runs", hidden, true).statusCode());
        String malformed = "{\"question\":\"inspect sample\",\"context\":{\"sampleIds\":[\"sample:bad\"]}}";
        assertEquals(400, post(anon, "/api/v1/agent/runs", malformed, true).statusCode());
    }

    @Test void missingNumericValueRemainsNullAndNeverBecomesZero() throws Exception {
        String id = candidate("OIL_CONTENT");
        String old = scalar("SELECT CAST(value_numeric AS CHAR) FROM scientific_lipid_observation WHERE candidate_id='" + id + "'");
        try {
            sql("UPDATE scientific_lipid_observation SET value_numeric=NULL WHERE candidate_id='" + id + "'");
            var response = get(admin(), "/api/v1/admin/workbench/points?sampleIds=sample:" + scalar("SELECT sample_id FROM scientific_lipid_observation WHERE candidate_id='" + id + "'") + "&kind=OIL_CONTENT");
            assertEquals(200, response.statusCode(), response.body());
            Map<?,?> page = object(response.body());
            Map<?,?> row = (Map<?,?>) ((List<?>) page.get("items")).getFirst();
            assertNull(row.get("value"), "missing numeric values must stay absent/null, never become zero");
            assertNotNull(row.get("rawValue"), "raw reported text is retained separately");
            assertNotEquals("0", row.get("rawValue"));
        } finally { sql("UPDATE scientific_lipid_observation SET value_numeric=" + old + " WHERE candidate_id='" + id + "'"); }
    }

    @Test void courseSampleKeepsItsExplicitPlantRelationAndTranscriptionIsNotAnAssay() throws Exception {
        String sample=scalar("SELECT id FROM research_sample ORDER BY id LIMIT 1"),plant=scalar("SELECT plant_id FROM research_sample WHERE id="+sample);
        var c=admin();var page=get(c,"/api/v1/admin/workbench/samples?plantId="+plant+"&limit=20");assertEquals(200,page.statusCode(),page.body());assertTrue(page.body().contains("course-sample:"+sample));assertFalse(page.body().contains("sample:"+scalar("SELECT sample_id FROM scientific_lipid_observation LIMIT 1")));
        var response=get(c,"/api/v1/admin/workbench/points?sampleIds=course-sample:"+sample);assertEquals(200,response.statusCode(),response.body());var rows=(List<Map<String,Object>>)object(response.body()).get("items");assertFalse(rows.isEmpty());assertNull(rows.getFirst().get("method"));assertNull(rows.getFirst().get("basis"));assertEquals("CANDIDATE",rows.getFirst().get("publicationState"));assertEquals("LEGACY_REVIEW_REQUIRED",rows.getFirst().get("reviewState"));
        var profile=get(c,"/api/v1/admin/workbench/plants/"+plant);assertEquals(200,profile.statusCode(),profile.body());assertTrue(profile.body().contains("plant:"+plant));
    }

    @Test void pointPaginationIsBoundedAndEvidenceLookupCanReachPastFirstPage() throws Exception {
        String seed=candidate("FAME_COMPOSITION"),sample=scalar("SELECT sample_id FROM scientific_lipid_observation WHERE candidate_id='"+seed+"'");
        // Explicit disposable technical fixtures; never inserted into the production schema.
        try {
            for(int i=0;i<65;i++) {
                String id=String.format("%064x",i+1);
                sql("INSERT INTO scientific_candidate(id,source_id,pipeline,locator,raw_payload,normalized_payload,content_sha256,normalization_state,reason) SELECT '"+id+"',source_id,pipeline,JSON_OBJECT('fixture','WORKBENCH_TECHNICAL_ONLY','row',"+i+"),raw_payload,normalized_payload,SHA2('workbench-fixture-"+i+"',256),normalization_state,reason FROM scientific_candidate WHERE id='"+seed+"'");
                sql("INSERT INTO scientific_lipid_observation SELECT '"+id+"',source_id,sample_id,kind,raw_value,value_numeric,unit,basis,analyte FROM scientific_lipid_observation WHERE candidate_id='"+seed+"'");
            }
            var c=admin();String path="/api/v1/admin/workbench/points?sampleIds=sample:"+sample+"&limit=60";var first=get(c,path);assertEquals(200,first.statusCode(),first.body());var a=object(first.body());assertEquals(60,((List<?>)a.get("items")).size());assertEquals(true,a.get("hasMore"));
            var second=get(c,path+"&cursor="+URLEncoder.encode(a.get("nextCursor").toString(),StandardCharsets.UTF_8));assertEquals(200,second.statusCode(),second.body());var b=object(second.body());assertEquals(6,((List<?>)b.get("items")).size());assertEquals(false,b.get("hasMore"));
            var ev=get(c,"/api/v1/admin/workbench/points/candidate:"+seed+"/evidence");assertEquals(200,ev.statusCode(),ev.body());assertTrue(ev.body().contains(seed));
            assertEquals(400,get(c,"/api/v1/admin/workbench/points?sampleIds=sample:"+sample+"&limit=61").statusCode());
        } finally {sql("DELETE FROM scientific_lipid_observation WHERE candidate_id REGEXP '^0{60}'");sql("DELETE FROM scientific_candidate WHERE JSON_UNQUOTE(JSON_EXTRACT(locator,'$.fixture'))='WORKBENCH_TECHNICAL_ONLY'");}
    }

    @Test void revocationRemovesFactFromCurrentPublicWorkbenchReads() throws Exception {
        String id = candidate("OIL_CONTENT");
        permitCandidateSources(id);
        decision(id, "APPROVED", 0);
        String sample = scalar("SELECT sample_id FROM scientific_lipid_observation WHERE candidate_id='" + id + "'");
        var c = login(RESEARCHER_EMAIL); String url = "/api/v1/workbench/points?sampleIds=sample:" + sample + "&kind=OIL_CONTENT";
        assertTrue(get(c, url).body().contains(id));
        decision(id, "REVOKED", 1);
        assertFalse(get(c, url).body().contains(id));
    }
}
