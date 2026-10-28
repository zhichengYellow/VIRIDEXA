package org.viridexa.api.account;
import java.util.*;
import java.util.concurrent.Semaphore;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.servlet.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.authentication.*;
import org.springframework.web.server.ResponseStatusException;
import org.viridexa.api.config.SessionAuthentication;
import org.viridexa.api.protection.LoginProtection;
@RestController
@RequestMapping("/api/v1/account")
public class AccountController {
 public record Registration(@NotBlank @Email @Size(max=254)String email,@NotBlank @Size(min=2,max=60)String displayName,@NotBlank @Size(max=200)String password,@Size(max=2048)String turnstileToken){@JsonAnySetter public void unknown(String key,Object value){throw new IllegalArgumentException("Unknown account field");}@Override public String toString(){return "Registration[REDACTED]";}}
 public record Login(@NotBlank @Email @Size(max=254)String email,@NotBlank @Size(max=200)String password,@Size(max=2048)String turnstileToken){@JsonAnySetter public void unknown(String key,Object value){throw new IllegalArgumentException("Unknown account field");}@Override public String toString(){return "Login[REDACTED]";}}
 private final LoginProtection protection;
 private final Semaphore inFlight=new Semaphore(2);
 private final AccountService service;private final AuthenticationManager manager;private final AccountRateLimiter limiter;
 public AccountController(AccountService service,AuthenticationManager manager,AccountRateLimiter limiter,LoginProtection protection){this.protection=protection;this.service=service;this.manager=manager;this.limiter=limiter;}
 @PostMapping("/register") public ResponseEntity<AccountService.Profile> register(@Valid @RequestBody Registration input,HttpServletRequest request,HttpServletResponse response,Authentication current){anonymous(current);String email=AccountRules.email(input.email());limiter.check("register",request.getRemoteAddr(),email);enter();try{protection.registration(input.turnstileToken());var profile=service.register(email,input.displayName(),input.password());var auth=UsernamePasswordAuthenticationToken.authenticated(profile.email(),null,List.of(new SimpleGrantedAuthority("ROLE_MEMBER")));SessionAuthentication.establish(auth,request,response);return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(profile);}finally{inFlight.release();}}
 @PostMapping("/login") public ResponseEntity<AccountService.Profile> login(@Valid @RequestBody Login input,HttpServletRequest request,HttpServletResponse response){String email=AccountRules.email(input.email());limiter.check("login",request.getRemoteAddr(),email);enter();try{protection.beforeLogin(email,request.getRemoteAddr(),input.turnstileToken());var auth=manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email,input.password()));var profile=service.profile(auth.getName());protection.succeeded(email);SessionAuthentication.establish(auth,request,response);return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profile);}catch(AuthenticationException|IllegalArgumentException e){protection.failed(email,request.getRemoteAddr());throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"邮箱或密码不正确");}finally{inFlight.release();}}
 @GetMapping("/session") public ResponseEntity<AccountService.Profile> session(Authentication auth){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.profile(auth.getName()));}
 @PostMapping("/logout") public ResponseEntity<Void> logout(HttpServletRequest request){SessionAuthentication.logout(request);return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();}
 private void enter(){if(!inFlight.tryAcquire())throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"账户服务繁忙，请稍后重试");}
 private static void anonymous(Authentication auth){if(auth!=null&&auth.isAuthenticated()&&!(auth instanceof AnonymousAuthenticationToken))throw new ResponseStatusException(HttpStatus.CONFLICT,"请先退出当前账户再注册");}
}
