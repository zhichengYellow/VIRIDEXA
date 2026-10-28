package org.viridexa.api.protection;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.properties.ConfigurationProperties;
import jakarta.annotation.PostConstruct;
@Component @ConfigurationProperties(prefix="viridexa.turnstile")
public class TurnstileProperties {
 private boolean enabled; private String mode="production",siteKey="",secret="",hostnames="";private int timeoutSeconds=4;
 @PostConstruct public void validate(){if(timeoutSeconds<1||timeoutSeconds>10)throw new IllegalArgumentException("Turnstile timeout must be 1–10 seconds");if(!enabled)return;if(!Set.of("test","production").contains(mode)||siteKey.isBlank()||secret.isBlank()||hosts().isEmpty())throw new IllegalArgumentException("Incomplete Turnstile configuration");if(mode.equals("production")&&(siteKey.matches("[123]x0+.*")||secret.matches("[123]x0+.*")||hosts().stream().anyMatch(h->h.equals("localhost")||h.matches("[0-9.:]+"))))throw new IllegalArgumentException("Production Turnstile cannot use test keys or local hostnames");}
 public Set<String> hosts(){var result=new HashSet<String>();for(String h:hostnames.split(","))if(!h.isBlank())result.add(h.trim().toLowerCase(Locale.ROOT));return Set.copyOf(result);}
 public boolean isEnabled(){return enabled;}public void setEnabled(boolean v){enabled=v;}public String getMode(){return mode;}public void setMode(String v){mode=v;}public String getSiteKey(){return siteKey;}public void setSiteKey(String v){siteKey=v;}public String getSecret(){return secret;}public void setSecret(String v){secret=v;}public String getHostnames(){return hostnames;}public void setHostnames(String v){hostnames=v;}public int getTimeoutSeconds(){return timeoutSeconds;}public void setTimeoutSeconds(int v){timeoutSeconds=v;}
}
