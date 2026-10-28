package org.viridexa.api.account;
import java.util.*;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
/** Bounded in-memory abuse protection. Nginx also limits auth requests by client IP. */
@Component
public class AccountRateLimiter {
 private record Window(long started,int count){}
 private final Map<String,Window> windows=new HashMap<>();private final Clock clock;
 public AccountRateLimiter(){this(Clock.systemUTC());}AccountRateLimiter(Clock clock){this.clock=clock;}
 public synchronized void check(String kind,String address,String email){long now=clock.millis();windows.entrySet().removeIf(e->now-e.getValue().started>3_600_000);hit(kind+":ip:"+digest(address),kind.equals("register")?100:120,kind.equals("register")?3_600_000:60_000,now);hit(kind+":identity:"+digest(email),kind.equals("register")?6:12,600_000,now);}
 private void hit(String key,int max,long duration,long now){var old=windows.get(key);if(old!=null&&now-old.started<duration){if(old.count>=max)throw limited();windows.put(key,new Window(old.started,old.count+1));}else{if(windows.size()>=10000)throw limited();windows.put(key,new Window(now,1));}}
 private static ResponseStatusException limited(){return new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"操作过于频繁，请稍后再试"){ @Override public org.springframework.http.HttpHeaders getHeaders(){var h=new org.springframework.http.HttpHeaders();h.set("Retry-After","600");return h;} };}
 private static String digest(String text){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
}
