package org.viridexa.api.protection;
import java.util.*;import org.springframework.web.bind.annotation.*;import org.springframework.http.*;
@RestController
public class SecurityMetadataController {
 private final TurnstileProperties properties;private final ClientIpFilter proxy;
 public SecurityMetadataController(TurnstileProperties p,ClientIpFilter f){properties=p;proxy=f;}
 @GetMapping("/api/v1/security/meta") public ResponseEntity<Map<String,Object>> meta(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("turnstileEnabled",properties.isEnabled(),"turnstileMode",properties.isEnabled()?properties.getMode():"disabled","siteKey",properties.isEnabled()?properties.getSiteKey():"","registrationChallengeRequired",properties.isEnabled(),"loginChallengeMode","progressive","proxyTrustConfigured",proxy.configured(),"singleInstance",true,"notice","技术防护不保证阻止所有自动化或 DDoS；真实 Cloudflare 与公网部署需另行验收。"));}
}
