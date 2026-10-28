package org.viridexa.api.agent;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenAiModelProvider implements ModelProvider {
    private static final int MAX_BODY = 128 * 1024;
    private final AgentProperties properties;
    private final ObjectMapper mapper;
    private final HttpClient client;
    private final OpenRouterBudget budget;
    private final boolean protocolFixture;

    @org.springframework.beans.factory.annotation.Autowired
    public OpenAiModelProvider(AgentProperties properties, ObjectMapper mapper) {this(properties,mapper,false);}
    OpenAiModelProvider(AgentProperties properties,ObjectMapper mapper,boolean protocolFixture) {
        this.protocolFixture=protocolFixture;
        this.properties = properties; this.mapper = mapper; this.budget = new OpenRouterBudget(properties);
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    @Override public boolean configured() { return !budget.disabled() && properties.isEnabled() && nonblank(properties.getApiKey()) && nonblank(properties.getModel()); }
    public Map<String,Object> freeStatus(){try{return budget.status();}catch(RuntimeException e){return Map.of("dailyLimit",30,"minuteLimit",5,"userDailyModelLimit",10,"usedToday",-1,"killSwitch",true,"storageUnavailable",true);}}
    @Override public String modelName() { return properties.getModel(); }

    @Override public AgentContract.ModelTurn complete(List<Map<String,Object>> messages, List<Map<String,Object>> tools, Duration timeout) throws Exception {
        if (!configured()) throw new ProviderException("MODEL_NOT_CONFIGURED");
        properties.validate();
        if (messages == null || tools == null || timeout == null || timeout.isZero() || timeout.isNegative()) throw new ProviderException("MODEL_INVALID_REQUEST");
        if (timeout.compareTo(Duration.ofSeconds(properties.getModelTimeoutSeconds())) > 0) timeout = Duration.ofSeconds(properties.getModelTimeoutSeconds());
        try {
            if (mapper.writeValueAsBytes(messages).length > properties.getMaxInputChars() * 4L
                    || mapper.writeValueAsBytes(tools).length > MAX_BODY / 2
                    || messages.stream().mapToInt(m -> m.toString().length()).sum() > properties.getMaxInputChars())
                throw new ProviderException("MODEL_REQUEST_TOO_LARGE");
        } catch (ProviderException e) { throw e; }
        catch (Exception e) { throw new ProviderException("MODEL_INVALID_REQUEST"); }
        long deadline=System.nanoTime()+timeout.toNanos();
        URI endpoint;
        try { endpoint = URI.create(properties.getBaseUrl().replaceAll("/+$", "") + "/chat/completions"); }
        catch (RuntimeException e) { throw new ProviderException("MODEL_INVALID_ENDPOINT"); }
        // Loopback HTTP exists solely for deterministic protocol tests. Every remote endpoint is free-only OpenRouter.
        boolean remote = !protocolFixture;
        if(protocolFixture&&!("http".equals(endpoint.getScheme())&&Set.of("127.0.0.1","localhost","::1").contains(endpoint.getHost())))throw new ProviderException("MODEL_FIXTURE_ENDPOINT_REJECTED");
        Set<String> safeModels=Set.of();
        if(remote) {
            if(!"https://openrouter.ai/api/v1".equals(properties.getBaseUrl()))throw new ProviderException("MODEL_ENDPOINT_NOT_ALLOWED");
            if(budget.disabled())throw new ProviderException("MODEL_KILL_SWITCH");
            HttpRequest metadata=HttpRequest.newBuilder(URI.create("https://openrouter.ai/api/v1/models")).timeout(Duration.ofSeconds(4)).GET().build();
            CompletableFuture<HttpResponse<byte[]>> check=client.sendAsync(metadata,info->new BoundedBodySubscriber(4*1024*1024));
            try {var result=check.get(Math.min(4000,timeout.toMillis()),TimeUnit.MILLISECONDS);if(result.statusCode()!=200)throw new ProviderException("MODEL_PRICING_UNAVAILABLE");safeModels=OpenRouterPolicy.freeToolModels(mapper.readTree(result.body()));}
            catch(Exception e){check.cancel(true);if(e instanceof InterruptedException)Thread.currentThread().interrupt();throw new ProviderException("MODEL_PRICING_UNAVAILABLE");}
            try{OpenRouterPolicy.validateModel(properties.getModel(),safeModels,properties.getFreeModelAllowlist());}catch(ProviderException e){budget.trip(e.getCode());throw e;}
        }
        Map<String,Object> payload = new LinkedHashMap<>();
        payload.put("model", properties.getModel()); payload.put("messages", messages); payload.put("tools", tools);
        payload.put("tool_choice", "auto"); payload.put("parallel_tool_calls", false); payload.put("max_completion_tokens", properties.getMaxOutputTokens());
        if(remote){payload.remove("parallel_tool_calls");payload.remove("max_completion_tokens");payload.put("max_tokens",properties.getMaxOutputTokens());payload.put("provider",OpenRouterPolicy.routing());payload.put("plugins",List.of());payload.put("stream",false);}
        byte[] requestBody;
        try { requestBody = mapper.writeValueAsBytes(payload); }
        catch (Exception e) { throw new ProviderException("MODEL_INVALID_REQUEST"); }
        if (requestBody.length > MAX_BODY) throw new ProviderException("MODEL_REQUEST_TOO_LARGE");
        timeout=Duration.ofNanos(Math.max(1,deadline-System.nanoTime()));
        if(Thread.currentThread().isInterrupted()||timeout.toMillis()<1)throw new ProviderException("MODEL_TIMEOUT");
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(timeout).header("Authorization", "Bearer " + properties.getApiKey())
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(requestBody)).build();
        String reservation=remote?budget.reserve():null;
        budget.attempted();
        CompletableFuture<HttpResponse<byte[]>> future=client.sendAsync(request, info->new BoundedBodySubscriber(MAX_BODY));
        HttpResponse<byte[]> response;
        try { response=future.get(timeout.toMillis(),TimeUnit.MILLISECONDS); }
        catch(TimeoutException e) { future.cancel(true); throw new ProviderException("MODEL_TIMEOUT"); }
        catch(InterruptedException e) { future.cancel(true); Thread.currentThread().interrupt(); throw new ProviderException("MODEL_INTERRUPTED"); }
        catch(ExecutionException e) {
            Throwable cause=e.getCause();
            if(cause instanceof ResponseTooLargeException) throw new ProviderException("MODEL_RESPONSE_TOO_LARGE");
            if(cause instanceof HttpTimeoutException) throw new ProviderException("MODEL_TIMEOUT");
            throw new ProviderException("MODEL_CONNECTION_FAILED");
        } catch(CancellationException e) { throw new ProviderException("MODEL_INTERRUPTED"); }
        byte[] body=response.body();
        if(response.statusCode()==429) throw new ProviderException("MODEL_QUOTA_EXHAUSTED");
        if(response.statusCode()<200||response.statusCode()>=300) throw new ProviderException("MODEL_HTTP_"+response.statusCode());
        try {
            JsonNode root=mapper.readTree(body);
            if(remote) {
                try {String actual=OpenRouterPolicy.validateResponse(root,safeModels);budget.result(reservation,actual,validUsage(root.path("usage").path("prompt_tokens"))?root.path("usage").path("prompt_tokens").longValue():null,validUsage(root.path("usage").path("completion_tokens"))?root.path("usage").path("completion_tokens").longValue():null,"0");}
                catch(ProviderException e){budget.result(reservation,root.path("model").asString("").matches("[a-zA-Z0-9_./:-]{1,160}")?root.path("model").asString():"UNVERIFIED",null,null,root.path("usage").path("cost").isNumber()?root.path("usage").path("cost").asString():"UNKNOWN");budget.trip(e.getCode());throw e;}
            }
            var parsed=parse(root, tools);
            return remote?new AgentContract.ModelTurn(parsed.assistantMessage(),parsed.calls(),parsed.selectedEvidenceIds(),parsed.inputTokens(),parsed.outputTokens(),root.path("model").asString(),"0"):parsed;
        }
        catch (ProviderException e) { throw e; }
        catch (Exception e) { if(remote)budget.trip("MODEL_INVALID_RESPONSE");throw new ProviderException("MODEL_INVALID_RESPONSE"); }
    }

    private AgentContract.ModelTurn parse(JsonNode root,List<Map<String,Object>> tools) throws Exception {
        JsonNode choices=root.path("choices"); if(!choices.isArray()||choices.isEmpty()) throw new ProviderException("MODEL_INVALID_RESPONSE");
        JsonNode msg=choices.get(0).path("message"); String content=msg.path("content").isString()?msg.path("content").stringValue():null;
        JsonNode rawCalls=msg.path("tool_calls"); List<AgentContract.ToolCall> calls=new ArrayList<>(); Set<String> ids=new HashSet<>();
        if(rawCalls.isArray()) {
            if(rawCalls.size()>properties.getMaxToolCalls()) throw new ProviderException("MODEL_TOO_MANY_TOOL_CALLS");
            Set<String> allowed=new HashSet<>(); for(Map<String,Object> t:tools){Object f=t.get("function");if(f instanceof Map<?,?> fm&&fm.get("name") instanceof String n)allowed.add(n);}
            for(JsonNode c:rawCalls) {
                String id=c.path("id").isString()?c.path("id").stringValue():null, name=c.path("function").path("name").isString()?c.path("function").path("name").stringValue():null;
                if(!nonblank(id)||id.length()>128||id.chars().anyMatch(ch->Character.isISOControl(ch))||!ids.add(id)||!allowed.contains(name)) throw new ProviderException("MODEL_INVALID_TOOL_CALL");
                JsonNode args;
                try { args=mapper.readTree(c.path("function").path("arguments").stringValue()); }
                catch(Exception e) { throw new ProviderException("MODEL_INVALID_TOOL_ARGUMENTS"); }
                if(!args.isObject()) throw new ProviderException("MODEL_INVALID_TOOL_ARGUMENTS");
                Map<String,Object> argMap=mapper.convertValue(args,Map.class);
                calls.add(new AgentContract.ToolCall(id,name,argMap));
            }
        }
        List<String> selected=new ArrayList<>();
        String safeContent = null;
        if(content!=null)try {
            JsonNode result=mapper.readTree(content);
            if(result.isObject()&&result.path("selectedEvidenceIds").isArray()) {
                boolean valid=true;
                for(JsonNode id:result.path("selectedEvidenceIds")) if(!id.isString()||!nonblank(id.stringValue())) { valid=false; break; }
                if(valid) { safeContent=content; for(JsonNode id:result.path("selectedEvidenceIds"))selected.add(id.stringValue()); }
            }
        } catch(Exception ignored) { /* discard malformed model text */ }
        Map<String,Object> assistant=new LinkedHashMap<>(); assistant.put("role","assistant"); if(safeContent!=null)assistant.put("content",safeContent);
        if(!calls.isEmpty()) { List<Map<String,Object>> out=new ArrayList<>(); for(AgentContract.ToolCall c:calls)out.add(Map.of("id",c.id(),"type","function","function",Map.of("name",c.name(),"arguments",mapper.writeValueAsString(c.arguments())))); assistant.put("tool_calls",out); }
        JsonNode usage=root.path("usage"); Long input=validUsage(usage.path("prompt_tokens"))?usage.path("prompt_tokens").longValue():null;
        Long output=validUsage(usage.path("completion_tokens"))?usage.path("completion_tokens").longValue():null;
        return new AgentContract.ModelTurn(assistant,List.copyOf(calls),List.copyOf(selected),input,output);
    }
    private static boolean validUsage(JsonNode node){return node.isIntegralNumber()&&node.canConvertToLong()&&node.longValue()>=0&&node.longValue()<=10_000_000;}
    private static boolean nonblank(String s){return s!=null&&!s.isBlank();}
    private static final class ResponseTooLargeException extends RuntimeException { }
    private static final class BoundedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
        private final int limit;
        private final ByteArrayOutputStream output=new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> body=new CompletableFuture<>();
        private Flow.Subscription subscription;
        BoundedBodySubscriber(int limit){this.limit=limit;}
        @Override public CompletionStage<byte[]> getBody(){return body;}
        @Override public void onSubscribe(Flow.Subscription s){subscription=s;s.request(1);}
        @Override public void onNext(List<ByteBuffer> buffers){
            long incoming=buffers.stream().mapToLong(ByteBuffer::remaining).sum();
            if(output.size()+incoming>limit){subscription.cancel();body.completeExceptionally(new ResponseTooLargeException());return;}
            for(ByteBuffer b:buffers){byte[] chunk=new byte[b.remaining()];b.get(chunk);output.writeBytes(chunk);}
            subscription.request(1);
        }
        @Override public void onError(Throwable t){body.completeExceptionally(t);}
        @Override public void onComplete(){body.complete(output.toByteArray());}
    }
    public static final class ProviderException extends RuntimeException {
        private final String code; public ProviderException(String code){super(code);this.code=code;} public String getCode(){return code;}
    }
}
