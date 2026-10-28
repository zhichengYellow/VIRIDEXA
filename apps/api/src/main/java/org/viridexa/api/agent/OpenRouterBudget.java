package org.viridexa.api.agent;

import java.nio.channels.FileChannel;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.function.Function;
import tools.jackson.databind.json.JsonMapper;

/** Durable, cross-process admission. A persisted reservation is never refunded, including network failures. */
final class OpenRouterBudget {
    private static final Object LOCK = new Object();
    private static final Set<Path> STOPPED = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static final ThreadLocal<Runnable> ATTEMPT = new ThreadLocal<>();
    private static final ThreadLocal<String> OWNER = new ThreadLocal<>();
    private final Path directory;
    private final Clock clock;
    private final JsonMapper json = JsonMapper.builder().build();
    OpenRouterBudget(AgentProperties p) { this(Path.of(p.getRuntimeDirectory()), Clock.systemUTC()); }
    OpenRouterBudget(Path directory, Clock clock) { this.directory=directory.toAbsolutePath(); this.clock=clock; }
    static AutoCloseable owner(String value) { String previous=OWNER.get(); OWNER.set(value); return ()->{if(previous==null)OWNER.remove();else OWNER.set(previous);}; }
    private static RuntimeException failure(String code) { return new OpenAiModelProvider.ProviderException(code); }
    boolean disabled() { return STOPPED.contains(directory)||Files.exists(directory.resolve("OPENROUTER_DISABLED")); }
    static AutoCloseable observe(Runnable listener){ATTEMPT.set(listener);return ATTEMPT::remove;}
    void attempted(){Runnable callback=ATTEMPT.get();if(callback!=null)callback.run();}
    String reserve() {
        return locked(state->{
            if(disabled())throw failure("MODEL_KILL_SWITCH");
            String day=LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai"))).toString();
            state.put("day",day);
            List<Map<String,Object>> attempts=(List<Map<String,Object>>)state.get("attempts");
            String user=AgentQuota.hash(Objects.toString(OWNER.get(),"server"));
            long now=clock.millis();
            if(attempts.stream().filter(a->day.equals(attemptDay(a))).count()>=30 || attempts.stream().filter(a->day.equals(attemptDay(a))&&user.equals(a.get("ownerHash"))).count()>=10)
                throw failure("MODEL_DAILY_QUOTA_EXHAUSTED");
            // Retain previous-minute reservations across a daily rollover too.
            List<Number> recent=(List<Number>)state.getOrDefault("recent",new ArrayList<>());
            recent=new ArrayList<>(recent.stream().filter(n->n.longValue()>now-60000).toList());
            if(recent.size()>=5)throw failure("MODEL_MINUTE_QUOTA_EXHAUSTED");
            recent.add(now);state.put("recent",recent);
            String id=UUID.randomUUID().toString();
            attempts.add(new LinkedHashMap<>(Map.of("id",id,"ownerHash",user,"at",now,"status","RESERVED_UNKNOWN_USAGE")));
            return id;
        });
    }
    void result(String id, String model, Long input, Long output, String cost) {
        locked(state->{for(Map<String,Object> a:(List<Map<String,Object>>)state.getOrDefault("attempts",List.of()))if(id.equals(a.get("id"))){a.put("model",model);a.put("cost",cost);if(input!=null)a.put("inputTokens",input);if(output!=null)a.put("outputTokens",output);a.put("status","0".equals(cost)?"ZERO_COST_RESPONSE":"COST_UNCONFIRMED_STOPPED");}return null;});
    }
    void trip(String code) {
        STOPPED.add(directory);
        synchronized(LOCK) { try { Files.createDirectories(directory); Files.writeString(directory.resolve("OPENROUTER_DISABLED"),code+"\n",StandardOpenOption.CREATE,StandardOpenOption.TRUNCATE_EXISTING);try(var out=FileChannel.open(directory.resolve("OPENROUTER_DISABLED"),StandardOpenOption.WRITE)){out.force(true);} }
            catch(Exception e){throw failure("MODEL_BUDGET_STORAGE_UNAVAILABLE");} }
    }
    Map<String,Object> status() { return locked(state->Map.of("dailyLimit",30,"minuteLimit",5,"userDailyModelLimit",10,"usedToday",(int)((List<Map<String,Object>>)state.getOrDefault("attempts",List.of())).stream().filter(a->LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai"))).toString().equals(attemptDay(a))).count(),"killSwitch",disabled())); }
    private static String attemptDay(Map<String,Object> a){return Instant.ofEpochMilli(((Number)a.get("at")).longValue()).atZone(ZoneId.of("Asia/Shanghai")).toLocalDate().toString();}
    private <T>T locked(Function<Map<String,Object>,T> work) {
        synchronized(LOCK) {
            try { Files.createDirectories(directory);
                try(var channel=FileChannel.open(directory.resolve("openrouter.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);var lock=channel.lock()) {
                    Path file=directory.resolve("openrouter-budget.json");
                    Map<String,Object> state=Files.exists(file)?new LinkedHashMap<>(json.readValue(Files.readString(file),Map.class)):new LinkedHashMap<>();
                    // A malformed ledger never resets admission to zero.
                    if(Files.exists(file)&&(!(state.get("attempts") instanceof List<?>)||!(state.get("day") instanceof String)))throw failure("MODEL_BUDGET_STORAGE_UNAVAILABLE");
                    if(!Files.exists(file)){state.put("day",LocalDate.now(clock.withZone(ZoneId.of("Asia/Shanghai"))).toString());state.put("attempts",new ArrayList<>());state.put("recent",new ArrayList<>());}
                    T result=work.apply(state);Path temp=Files.createTempFile(directory,"openrouter-",".tmp");
                    try { Files.writeString(temp,json.writeValueAsString(state));try(var out=FileChannel.open(temp,StandardOpenOption.WRITE)){out.force(true);}Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
                    finally {Files.deleteIfExists(temp);}return result;
                }
            } catch(OpenAiModelProvider.ProviderException e){throw e;}
            catch(Exception e){throw failure("MODEL_BUDGET_STORAGE_UNAVAILABLE");}
        }
    }
}
