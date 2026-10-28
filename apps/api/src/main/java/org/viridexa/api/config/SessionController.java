package org.viridexa.api.config;
import java.util.*;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.authentication.*;
import org.springframework.security.core.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
public class SessionController {
 public record Login(@NotBlank @Size(max=254) String username,@NotBlank @Size(max=200) String password,@Size(max=2048) String turnstileToken) {@Override public String toString(){return "Login[REDACTED]";}}
 private final AuthenticationManager manager;
 private final org.viridexa.api.protection.LoginProtection protection;
 private final java.util.concurrent.Semaphore slots=new java.util.concurrent.Semaphore(2);
 public SessionController(AuthenticationManager manager,org.viridexa.api.protection.LoginProtection protection){this.manager=manager;this.protection=protection;}
 @GetMapping("/api/v1/csrf") public Map<String,String> csrf(CsrfToken token,HttpServletRequest request){synchronized(request.getSession()){return Map.of("token",token.getToken(),"headerName",token.getHeaderName());}}
 @PostMapping("/api/v1/admin/session")
 public Map<String,Object> login(@Valid @RequestBody Login login,HttpServletRequest request,HttpServletResponse response){
  if(!slots.tryAcquire())throw new org.viridexa.api.protection.ProtectionException(429,"LOGIN_BUSY","登录服务繁忙，请稍后重试。",5);
  try{
   protection.beforeLogin(login.username(),request.getRemoteAddr(),login.turnstileToken());
   var auth=manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(login.username(),login.password()));
   if(auth.getAuthorities().stream().noneMatch(a->a.getAuthority().equals("ROLE_ADMIN")))throw new ResponseStatusException(HttpStatus.FORBIDDEN,"需要管理员权限");
   protection.succeeded(login.username());
   SessionAuthentication.establish(auth,request,response);
   return identity(auth);
  }catch(AuthenticationException e){protection.failed(login.username(),request.getRemoteAddr());throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"登录失败");}finally{slots.release();}
 }
 @GetMapping("/api/v1/admin/session") public Map<String,Object> session(Authentication auth){return identity(auth);}
 @PostMapping("/api/v1/admin/session/logout") public void logout(HttpServletRequest request){SessionAuthentication.logout(request);}
 private Map<String,Object> identity(Authentication auth){return Map.of("username",auth.getName(),"roles",auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());}
}
