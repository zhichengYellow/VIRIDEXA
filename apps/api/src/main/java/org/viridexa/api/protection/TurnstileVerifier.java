package org.viridexa.api.protection;
import java.time.*;import java.util.*;import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;
import org.viridexa.api.agent.AgentQuota;
/** Cloudflare enforces global single-use; this bounded local cache also blocks simultaneous replays. */
@Component
public class TurnstileVerifier {
 private final TurnstileProperties p;private final TurnstileTransport transport;private final Clock clock;private final Map<String,Instant> spent=new HashMap<>();private final Semaphore slots=new Semaphore(2);
 @org.springframework.beans.factory.annotation.Autowired public TurnstileVerifier(TurnstileProperties p,TurnstileTransport transport){this(p,transport,Clock.systemUTC());}
 TurnstileVerifier(TurnstileProperties p,TurnstileTransport t,Clock c){this.p=p;transport=t;clock=c;}
 public void verify(String token,String action){if(!p.isEnabled())return;if(token==null||token.isBlank())throw error("CHALLENGE_REQUIRED","请先完成人机验证，再重新提交。");if(token.length()>2048)throw error("CHALLENGE_INVALID","验证内容无效，请重新验证。");if(!slots.tryAcquire())throw new ProtectionException(429,"CHALLENGE_BUSY","验证请求较多，请稍后重试。",5);
  try{String hash=AgentQuota.hash(token);synchronized(spent){Instant now=clock.instant();spent.entrySet().removeIf(e->e.getValue().isBefore(now.minusSeconds(360)));if(spent.containsKey(hash))throw error("CHALLENGE_REPLAY","验证已使用，请重新验证后提交。");if(spent.size()>=10000)throw new ProtectionException(503,"CHALLENGE_UNAVAILABLE","验证服务繁忙，请稍后重新验证。",30);spent.put(hash,now);}
   Map<String,Object> result;try{result=transport.verify(p.getSecret(),token,p.getTimeoutSeconds());}catch(Exception ignored){if(ignored instanceof InterruptedException)Thread.currentThread().interrupt();throw new ProtectionException(503,"CHALLENGE_UNAVAILABLE","人机验证服务暂不可用，请稍后重试；本次操作尚未执行。",10);}
   if(!Boolean.TRUE.equals(result.get("success"))){boolean expired=result.get("error-codes") instanceof List<?> errors&&errors.contains("timeout-or-duplicate");throw error(expired?"CHALLENGE_EXPIRED":"CHALLENGE_INVALID",expired?"验证已过期或使用过，请重新验证。":"人机验证未通过，请重试。");}
   if(!p.hosts().contains(String.valueOf(result.get("hostname")).toLowerCase(Locale.ROOT))||!action.equals(result.get("action")))throw error("CHALLENGE_INVALID","验证场景不匹配，请刷新页面重新验证。");
   try{Instant issued=Instant.parse(String.valueOf(result.get("challenge_ts")));if(issued.isBefore(clock.instant().minusSeconds(300))||issued.isAfter(clock.instant().plusSeconds(30)))throw error("CHALLENGE_EXPIRED","验证已过期，请重新验证。");}catch(java.time.format.DateTimeParseException invalid){throw error("CHALLENGE_INVALID","验证信息不完整，请重新验证。");}
  }finally{slots.release();}
 }
 private static ProtectionException error(String code,String message){return new ProtectionException(403,code,message,0);}
}
