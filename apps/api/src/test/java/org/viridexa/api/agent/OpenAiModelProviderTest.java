package org.viridexa.api.agent;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class OpenAiModelProviderTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void sendsChatCompletionAndParsesToolCallAndUsage() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        List<String> seen = new ArrayList<>();
        server.createContext("/v1/chat/completions", exchange -> {
            seen.add(exchange.getRequestHeaders().getFirst("Authorization"));
            seen.add(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            byte[] body = "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"tool_calls\":[{\"id\":\"call1\",\"type\":\"function\",\"function\":{\"name\":\"search\",\"arguments\":\"{\\\"q\\\":\\\"x\\\"}\"}}]}}],\"usage\":{\"prompt_tokens\":11,\"completion_tokens\":7}}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type","application/json"); exchange.sendResponseHeaders(200,body.length); exchange.getResponseBody().write(body); exchange.close();
        }); server.start();
        try {
            AgentProperties p=props(server); OpenAiModelProvider provider=new OpenAiModelProvider(p,mapper);
            var turn=provider.complete(List.of(Map.of("role","user","content","hello")),List.of(Map.of("type","function","function",Map.of("name","search"))),Duration.ofSeconds(2));
            assertEquals("Bearer secret",seen.get(0)); assertTrue(seen.get(1).contains("\"parallel_tool_calls\":false"));
            assertEquals("search",turn.calls().get(0).name()); assertEquals("x",turn.calls().get(0).arguments().get("q"));
            assertEquals(11L,turn.inputTokens()); assertEquals(7L,turn.outputTokens());
        } finally { server.stop(0); }
    }
    @Test void rejectsHttpOutsideLoopbackAndInvalidBounds() {
        AgentProperties p=new AgentProperties(); p.setBaseUrl("http://example.com/v1"); p.setModel("m");
        assertThrows(IllegalArgumentException.class,p::validate);
        p=new AgentProperties(); p.setMaxToolCalls(7); AgentProperties invalid=p;
        assertThrows(IllegalArgumentException.class,invalid::validate);
    }
    @Test void disabledProviderIsNotConfigured() { assertFalse(new OpenAiModelProvider(new AgentProperties(),mapper).configured()); }
    @Test void rejectsUnknownToolDuplicateAndMalformedToolArguments() throws Exception {
        assertCode(200,"{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"a\",\"function\":{\"name\":\"evil\",\"arguments\":\"{}\"}}]}}]}","MODEL_INVALID_TOOL_CALL");
        assertCode(200,"{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"a\",\"function\":{\"name\":\"search\",\"arguments\":\"not-json\"}}]}}]}","MODEL_INVALID_TOOL_ARGUMENTS");
        assertCode(200,"{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"a\",\"function\":{\"name\":\"search\",\"arguments\":\"{}\"}},{\"id\":\"a\",\"function\":{\"name\":\"search\",\"arguments\":\"{}\"}}]}}]}","MODEL_INVALID_TOOL_CALL");
        assertCode(200,"{\"choices\":[{\"message\":{\"tool_calls\":[{\"id\":\"\",\"function\":{\"name\":\"search\",\"arguments\":\"{}\"}}]}}]}","MODEL_INVALID_TOOL_CALL");
    }
    @Test void mapsQuotaAndOversizedResponsesToSafeErrors() throws Exception {
        assertCode(429,"secret provider details","MODEL_QUOTA_EXHAUSTED");
        String tooLarge="x".repeat(128*1024+1);
        assertCode(200,tooLarge,"MODEL_RESPONSE_TOO_LARGE");
    }
    @Test void omitsUnreportedUsageAndDiscardsNonJsonFinalContent() throws Exception {
        var turn=invoke(200,"{\"choices\":[{\"message\":{\"content\":\"private reasoning\"}}]}");
        assertNull(turn.inputTokens()); assertNull(turn.outputTokens()); assertFalse(turn.assistantMessage().containsKey("content"));
    }
    @Test void ignoresNegativeAndFractionalUsageCounts() throws Exception {
        var turn=invoke(200,"{\"choices\":[{\"message\":{\"content\":\"{}\"}}],\"usage\":{\"prompt_tokens\":-1,\"completion_tokens\":1.5}}");
        assertNull(turn.inputTokens()); assertNull(turn.outputTokens());
    }
    @Test void rejectsMalformedProviderEnvelopeWithoutLeakingBody() throws Exception {
        var error=assertThrows(OpenAiModelProvider.ProviderException.class,()->invoke(200,"upstream secret body"));
        assertEquals("MODEL_INVALID_RESPONSE",error.getCode()); assertFalse(error.getMessage().contains("secret"));
    }
    @Test void timesOutAndReportsOnlySafeCode() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        java.util.concurrent.atomic.AtomicInteger requests=new java.util.concurrent.atomic.AtomicInteger();
        server.createContext("/v1/chat/completions",e->{int n=requests.incrementAndGet();try{if(n==1){e.sendResponseHeaders(200,2);Thread.sleep(350);e.getResponseBody().write("{}".getBytes());}else{byte[] b="{\"choices\":[{\"message\":{\"content\":\"{\\\"selectedEvidenceIds\\\":[]}\"}}]}".getBytes();e.sendResponseHeaders(200,b.length);e.getResponseBody().write(b);}}catch(Exception ignored){}e.close();});server.start();
        try {
            OpenAiModelProvider provider=new OpenAiModelProvider(props(server),mapper);
            var ex=assertThrows(OpenAiModelProvider.ProviderException.class,()->provider.complete(List.of(Map.of("role","user","content","q")),tools(),Duration.ofMillis(50)));
            assertEquals("MODEL_TIMEOUT",ex.getCode());
            assertDoesNotThrow(()->provider.complete(List.of(Map.of("role","user","content","q")),tools(),Duration.ofSeconds(1)));
        } finally {server.stop(0);}
    }
    @Test void rejectsOversizedInputsBeforeRequest() throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0); server.start();
        try {
            AgentProperties p=props(server);p.setMaxInputChars(10);
            var ex=assertThrows(OpenAiModelProvider.ProviderException.class,()->new OpenAiModelProvider(p,mapper).complete(List.of(Map.of("role","user","content","x".repeat(20))),tools(),Duration.ofSeconds(1)));
            assertEquals("MODEL_REQUEST_TOO_LARGE",ex.getCode());
        } finally {server.stop(0);}
    }
    private void assertCode(int status,String body,String expected) throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",e->{byte[] b=body.getBytes(StandardCharsets.UTF_8);try{e.sendResponseHeaders(status,b.length);e.getResponseBody().write(b);}catch(Exception ignored){}e.close();});server.start();
        try {var ex=assertThrows(OpenAiModelProvider.ProviderException.class,()->new OpenAiModelProvider(props(server),mapper).complete(List.of(Map.of("role","user","content","q")),tools(),Duration.ofSeconds(1)));assertEquals(expected,ex.getCode());assertFalse(ex.getMessage().contains("secret"));} finally {server.stop(0);}
    }
    private AgentContract.ModelTurn invoke(int status,String body) throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/v1/chat/completions",e->{byte[] b=body.getBytes(StandardCharsets.UTF_8);try{e.sendResponseHeaders(status,b.length);e.getResponseBody().write(b);}catch(Exception ignored){}e.close();});server.start();
        try{return new OpenAiModelProvider(props(server),mapper).complete(List.of(Map.of("role","user","content","q")),tools(),Duration.ofSeconds(1));}finally{server.stop(0);}
    }
    private List<Map<String,Object>> tools(){return List.of(Map.of("type","function","function",Map.of("name","search")));}
    private AgentProperties props(HttpServer server) {
        AgentProperties p=new AgentProperties(); p.setEnabled(true); p.setApiKey("secret"); p.setModel("test-model");
        p.setBaseUrl("http://127.0.0.1:"+server.getAddress().getPort()+"/v1"); p.setAllowHttp(true); return p;
    }
}
