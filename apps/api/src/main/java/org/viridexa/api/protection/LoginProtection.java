package org.viridexa.api.protection;
import java.time.*;import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Autowired;
import org.viridexa.api.agent.AgentQuota;
/** Only failed authentication raises risk. Finite windows, hashed identities, no account-existence signal. */
@Component
public class LoginProtection {
 private record Window(long start,int count){}
 private final Map<String,Window> failures=new HashMap<>();private final Clock clock;private final TurnstileVerifier verifier;private final TurnstileProperties properties;
 @Autowired public LoginProtection(TurnstileVerifier v,TurnstileProperties p){this(v,p,Clock.systemUTC());}
 LoginProtection(TurnstileVerifier v,TurnstileProperties p,Clock c){verifier=v;properties=p;clock=c;}
 private String key(String prefix,String v){return prefix+AgentQuota.hash(v.toLowerCase(Locale.ROOT).strip());}
 private void prune(){long now=clock.millis();failures.entrySet().removeIf(e->now-e.getValue().start>=600000);}
 private int n(String key){var w=failures.get(key);return w==null?0:w.count;}
 public void beforeLogin(String email,String ip,String token){boolean challenge; synchronized(this){prune();int account=n(key("account:",email)),address=n(key("ip:",ip));if(account>=12||address>=60||failures.size()>=10000)throw new ProtectionException(429,"LOGIN_RATE_LIMITED","登录尝试较多，请稍后重试。",600);challenge=account>=3||address>=10;}if(properties.isEnabled()&&(challenge||token!=null&&!token.isBlank()))verifier.verify(token,"login");}
 public void registration(String token){verifier.verify(token,"register");}
 public synchronized void failed(String email,String ip){prune();for(String k:List.of(key("account:",email),key("ip:",ip))){if(!failures.containsKey(k)&&failures.size()>=10000)continue;var old=failures.get(k);failures.put(k,new Window(old==null?clock.millis():old.start,old==null?1:old.count+1));}}
 public synchronized void succeeded(String email){failures.remove(key("account:",email));}
}
