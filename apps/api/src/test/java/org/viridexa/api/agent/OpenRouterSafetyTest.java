package org.viridexa.api.agent;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

class OpenRouterSafetyTest {
 @TempDir Path directory;
 ObjectMapper json=new ObjectMapper();
 private String models="{\"data\":[{\"id\":\"openrouter/free\",\"pricing\":{\"prompt\":\"0\",\"completion\":\"0\"},\"supported_parameters\":[\"tools\"]},{\"id\":\"plant/model:free\",\"pricing\":{\"prompt\":\"0\",\"completion\":\"0\"},\"supported_parameters\":[\"tools\"]}]}";
 @Test void onlyExplicitFreeNamesWithLiveZeroPricesAndToolsPass()throws Exception{
  var safe=OpenRouterPolicy.freeToolModels(json.readTree(models));
  assertEquals(Set.of("openrouter/free","plant/model:free"),safe);
  assertDoesNotThrow(()->OpenRouterPolicy.validateModel("openrouter/free",safe,"openrouter/free"));
  assertThrows(RuntimeException.class,()->OpenRouterPolicy.validateModel("plant/model:free",safe,"openrouter/free"));
  assertThrows(RuntimeException.class,()->OpenRouterPolicy.validateModel("paid/model",Set.of("paid/model"),"paid/model"));
 }
 @Test void missingNonzeroAndAdditionalPricesFailClosed()throws Exception{
  for(String pricing:List.of("{}","{\"prompt\":\"0\"}","{\"prompt\":\"0\",\"completion\":\"0.01\"}","{\"prompt\":\"0\",\"completion\":\"0\",\"request\":\"0.01\"}","{\"prompt\":\"0\",\"completion\":\"0\",\"cache\":null}"))
   assertTrue(OpenRouterPolicy.freeToolModels(json.readTree("{\"data\":[{\"id\":\"openrouter/free\",\"pricing\":"+pricing+",\"supported_parameters\":[\"tools\"]}]}" )).isEmpty());
 }
 @Test void metadataMissingAndModelWithoutToolsAreRejected()throws Exception{
  assertThrows(RuntimeException.class,()->OpenRouterPolicy.freeToolModels(json.readTree("{}")));
  assertTrue(OpenRouterPolicy.freeToolModels(json.readTree(models.replace("[\"tools\"]","[]"))).isEmpty());
 }
 @Test void actualModelAndAllReturnedCostsMustBeVerified()throws Exception{
  var safe=Set.of("plant/model:free");
  assertEquals("plant/model:free",OpenRouterPolicy.validateResponse(json.readTree("{\"model\":\"plant/model:free\",\"usage\":{\"cost\":0,\"cost_details\":{\"upstream_inference_cost\":0}}}"),safe));
  for(String response:List.of("{\"model\":\"paid/model\",\"usage\":{\"cost\":0}}","{\"model\":\"plant/model:free\",\"usage\":{}}","{\"model\":\"plant/model:free\",\"usage\":{\"cost\":0.00001}}","{\"model\":\"plant/model:free\",\"usage\":{\"cost\":0,\"cost_details\":{\"upstream_inference_cost\":1}}}","{\"model\":\"plant/model:free\",\"usage\":{\"cost\":0,\"is_byok\":true}}"))assertThrows(RuntimeException.class,()->OpenRouterPolicy.validateResponse(json.readTree(response),safe));
 }
 @Test void providerCapIsZeroAndFallbacksAreDisabled(){var r=OpenRouterPolicy.routing();assertEquals(false,r.get("allow_fallbacks"));assertEquals(true,r.get("require_parameters"));assertEquals(0,((Map<?,?>)r.get("max_price")).get("prompt"));assertEquals(0,((Map<?,?>)r.get("max_price")).get("completion"));}
 @Test void concurrentInstancesCannotExceedFiveReservationsPerMinute()throws Exception{
  var now=Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"),ZoneOffset.UTC);AtomicInteger accepted=new AtomicInteger();
  try(var pool=Executors.newFixedThreadPool(8)){List<Future<?>> futures=new ArrayList<>();for(int i=0;i<20;i++)futures.add(pool.submit(()->{try{new OpenRouterBudget(directory,now).reserve();accepted.incrementAndGet();}catch(OpenAiModelProvider.ProviderException expected){}}));for(var f:futures)f.get();}
  assertEquals(5,accepted.get());assertEquals(5,new OpenRouterBudget(directory,now).status().get("usedToday"));
 }
 @Test void reservationsCountFailuresAndSurviveRestart()throws Exception{
  var now=Clock.fixed(Instant.parse("2026-10-28T10:00:00Z"),ZoneOffset.UTC);new OpenRouterBudget(directory,now).reserve();
  assertEquals(1,new OpenRouterBudget(directory,now).status().get("usedToday"));
  assertTrue(Files.readString(directory.resolve("openrouter-budget.json")).contains("RESERVED_UNKNOWN_USAGE"));
 }
 @Test void globalThirtyDailyLimitAndPerUserTenCannotBeRaised()throws Exception{
  Instant now=Instant.parse("2026-10-28T10:00:00Z");
  for(int i=0;i<30;i++)try(var owner=OpenRouterBudget.owner("user:"+(i%3))){new OpenRouterBudget(directory,Clock.fixed(now.plusSeconds(i*61),ZoneOffset.UTC)).reserve();}
  try(var owner=OpenRouterBudget.owner("fourth")){assertThrows(RuntimeException.class,()->new OpenRouterBudget(directory,Clock.fixed(now.plusSeconds(1900),ZoneOffset.UTC)).reserve());}
 }
 @Test void independentUserQuotaRejectsEleventhRequest()throws Exception{
  Instant now=Instant.parse("2026-10-28T10:00:00Z");for(int i=0;i<10;i++)new OpenRouterBudget(directory,Clock.fixed(now.plusSeconds(i*61),ZoneOffset.UTC)).reserve();
  assertThrows(RuntimeException.class,()->new OpenRouterBudget(directory,Clock.fixed(now.plusSeconds(700),ZoneOffset.UTC)).reserve());
  try(var owner=OpenRouterBudget.owner("second")){assertDoesNotThrow(()->new OpenRouterBudget(directory,Clock.fixed(now.plusSeconds(701),ZoneOffset.UTC)).reserve());}
 }
 @Test void midnightDoesNotResetRollingMinuteLimit()throws Exception{
  Instant now=Instant.parse("2026-10-28T15:59:50Z");for(int i=0;i<5;i++)new OpenRouterBudget(directory,Clock.fixed(now,ZoneOffset.UTC)).reserve();
  assertThrows(RuntimeException.class,()->new OpenRouterBudget(directory,Clock.fixed(now.plusSeconds(20),ZoneOffset.UTC)).reserve());
 }
 @Test void persistentKillSwitchStopsAllFurtherReservations()throws Exception{
  var budget=new OpenRouterBudget(directory,Clock.systemUTC());budget.reserve();budget.trip("MODEL_NONZERO_OR_UNKNOWN_COST");
  assertTrue(new OpenRouterBudget(directory,Clock.systemUTC()).disabled());assertThrows(RuntimeException.class,budget::reserve);
 }
 @Test void unreadableOrCorruptLedgerNeverResetsQuota()throws Exception{
  Files.writeString(directory.resolve("openrouter-budget.json"),"{broken");assertThrows(RuntimeException.class,()->new OpenRouterBudget(directory,Clock.systemUTC()).reserve());
 }
 @Test void remoteEndpointOverridesCannotBypassFreePolicy()throws Exception{
  AgentProperties p=new AgentProperties();p.setEnabled(true);p.setApiKey("isolated-fixture-not-real-key");p.setBaseUrl("https://example.invalid/api");
  var provider=new OpenAiModelProvider(p,json);var ex=assertThrows(OpenAiModelProvider.ProviderException.class,()->provider.complete(List.of(Map.of("role","user","content","test")),List.of(),Duration.ofSeconds(1)));assertEquals("MODEL_ENDPOINT_NOT_ALLOWED",ex.getCode());
 }
 @Test void publicConstructorCannotDisableFreePolicyUsingAllowHttp()throws Exception{
  AgentProperties p=new AgentProperties();p.setEnabled(true);p.setApiKey("isolated-fixture-not-real-key");p.setAllowHttp(true);p.setBaseUrl("http://127.0.0.1:1/v1");
  var ex=assertThrows(OpenAiModelProvider.ProviderException.class,()->new OpenAiModelProvider(p,json).complete(List.of(),List.of(),Duration.ofSeconds(1)));assertEquals("MODEL_ENDPOINT_NOT_ALLOWED",ex.getCode());
 }

 @Test void metadataBeforeFirstRequestDoesNotCorruptFreshLedger(){var budget=new OpenRouterBudget(directory,Clock.systemUTC());assertEquals(0,budget.status().get("usedToday"));assertDoesNotThrow(budget::reserve);assertEquals(1,budget.status().get("usedToday"));}

}
