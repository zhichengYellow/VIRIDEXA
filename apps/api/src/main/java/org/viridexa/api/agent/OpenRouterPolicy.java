package org.viridexa.api.agent;

import java.math.BigDecimal;
import java.util.*;
import tools.jackson.databind.JsonNode;
import static org.viridexa.api.agent.OpenAiModelProvider.ProviderException;

/** Fail closed. Metadata is fetched anew for every inference; no provider-side plugins are submitted. */
final class OpenRouterPolicy {
    static Set<String> freeToolModels(JsonNode root) {
        if(!root.path("data").isArray())throw new ProviderException("MODEL_PRICING_UNAVAILABLE");
        Set<String> safe=new HashSet<>();
        for(JsonNode m:root.path("data")) {
            String id=m.path("id").asString("");
            if(!(id.equals("openrouter/free")||id.endsWith(":free")))continue;
            JsonNode pricing=m.path("pricing");
            if(!pricing.isObject()||!zero(pricing.path("prompt"))||!zero(pricing.path("completion")))continue;
            boolean allZero=true;for(JsonNode n:pricing)if(!zero(n)){allZero=false;break;}
            boolean tools=false;for(JsonNode n:m.path("supported_parameters"))if("tools".equals(n.asString()))tools=true;
            if(allZero&&tools)safe.add(id);
        }
        return Set.copyOf(safe);
    }
    static void validateModel(String model,Set<String> safe,String allowlist) {
        boolean allowed=Arrays.stream(allowlist.split(",")).map(String::trim).anyMatch(model::equals);
        if(!allowed||!(model.equals("openrouter/free")||model.endsWith(":free"))||!safe.contains(model))throw new ProviderException("MODEL_ZERO_PRICE_POLICY_REJECTED");
    }
    static String validateResponse(JsonNode root,Set<String> safe) {
        String model=root.path("model").asString("");
        if(!safe.contains(model))throw new ProviderException("MODEL_UNVERIFIED_RESPONSE_MODEL");
        JsonNode usage=root.path("usage");
        if(!zero(usage.path("cost")))throw new ProviderException("MODEL_NONZERO_OR_UNKNOWN_COST");
        JsonNode details=usage.path("cost_details");
        if(details.isObject())for(JsonNode n:details)if(!n.isNull()&&!zero(n))throw new ProviderException("MODEL_NONZERO_OR_UNKNOWN_COST");
        if(usage.path("is_byok").asBoolean(false))throw new ProviderException("MODEL_BYOK_NOT_ALLOWED");
        return model;
    }
    static boolean zero(JsonNode n) {try{return (n.isNumber()||n.isString())&&new BigDecimal(n.asString()).signum()==0;}catch(Exception e){return false;}}
    static Map<String,Object> routing() {return Map.of("allow_fallbacks",false,"require_parameters",true,"max_price",Map.of("prompt",0,"completion",0,"request",0,"image",0));}
}
