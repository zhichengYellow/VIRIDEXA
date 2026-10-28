package org.viridexa.api.agent;

import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Single-instance durable daily budget. Unknown provider usage consumes the reservation. No questions or credentials persisted. */
@Component
public class AgentQuota {
 private final AgentProperties properties;
 private final Path file;
 private final JsonMapper json=JsonMapper.builder().build();
 private Map<String,Object> ledger=new LinkedHashMap<>();
 public AgentQuota(AgentProperties properties){this.properties=properties;file=Path.of(properties.getRuntimeDirectory()).toAbsolutePath().resolve("quota.json");load();}
 private void load(){try{Files.createDirectories(file.getParent());Path probe=Files.createTempFile(file.getParent(),"write-probe-",".tmp");Files.delete(probe);if(Files.exists(file))ledger=new LinkedHashMap<>(json.readValue(Files.readString(file),Map.class));}catch(Exception e){throw new IllegalStateException("AGENT_BUDGET_STORAGE_UNAVAILABLE");}}
 private String day(){return LocalDate.now(ZoneId.of("Asia/Shanghai")).toString();}
 private void roll(){if(!day().equals(ledger.get("day")))ledger=new LinkedHashMap<>(Map.of("day",day(),"requests",0L,"tokens",0L,"users",new LinkedHashMap<String,Object>()));}
 @SuppressWarnings("unchecked") private Map<String,Object> user(String owner){var users=(Map<String,Object>)ledger.get("users");return (Map<String,Object>)users.computeIfAbsent(hash(owner),k->new LinkedHashMap<>(Map.of("requests",0L,"tokens",0L)));}
 private static long n(Map<String,Object> m,String k){return ((Number)m.getOrDefault(k,0)).longValue();}
 private void save(){try{Path temp=Files.createTempFile(file.getParent(),"quota-",".tmp");Files.writeString(temp,json.writeValueAsString(ledger),StandardCharsets.UTF_8);Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(Exception e){throw new IllegalStateException("AGENT_BUDGET_STORAGE_UNAVAILABLE");}}
 public synchronized void start(String owner){roll();var u=user(owner);if(n(ledger,"requests")>=properties.getGlobalDailyRequests()||n(u,"requests")>=properties.getUserDailyRequests())throw new BudgetExceeded();ledger.put("requests",n(ledger,"requests")+1);u.put("requests",n(u,"requests")+1);save();}
 public record Reservation(long amount,String day){}
 public synchronized Reservation reserveTicket(String owner,long tokens){reserve(owner,tokens);return new Reservation(tokens,day());}
 public synchronized void settleTicket(String owner,Reservation reservation,Long actual){if(!day().equals(reservation.day()))return;settle(owner,reservation.amount(),actual);}
 public synchronized long reserve(String owner,long tokens){roll();var u=user(owner);if(tokens<1||n(ledger,"tokens")+tokens>properties.getGlobalDailyTokens()||n(u,"tokens")+tokens>properties.getUserDailyTokens())throw new BudgetExceeded();checkCost(n(ledger,"tokens")+tokens,properties.getGlobalDailyCost());checkCost(n(u,"tokens")+tokens,properties.getUserDailyCost());ledger.put("tokens",n(ledger,"tokens")+tokens);u.put("tokens",n(u,"tokens")+tokens);save();return tokens;}
 public synchronized void settle(String owner,long reservation,Long actual){roll();if(actual==null)return;var u=user(owner);long delta=Math.max(0,actual)-reservation;ledger.put("tokens",Math.max(0,n(ledger,"tokens")+delta));u.put("tokens",Math.max(0,n(u,"tokens")+delta));save();}
 // Conservative upper bound: price every input/output token at the higher configured rate.
 private void checkCost(long tokens,java.math.BigDecimal cap){if(cap==null)return;var price=properties.getInputCostPerMillion().max(properties.getOutputCostPerMillion());if(java.math.BigDecimal.valueOf(tokens).multiply(price).compareTo(cap.multiply(java.math.BigDecimal.valueOf(1_000_000)))>0)throw new BudgetExceeded();}
 public static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
 public static class BudgetExceeded extends RuntimeException {public BudgetExceeded(){super("AGENT_QUOTA_EXHAUSTED");}}
}
