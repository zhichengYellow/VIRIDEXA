package org.viridexa.api.config;
import jakarta.servlet.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.csrf.*;
public final class SessionAuthentication {
 private SessionAuthentication(){}
 public static void establish(Authentication auth,HttpServletRequest request,HttpServletResponse response){// A successful identity switch must not inherit the previous account's research runs.
 var previous=request.getSession(false);if(previous!=null)previous.removeAttribute("agentOwner");
 new ChangeSessionIdAuthenticationStrategy().onAuthentication(auth,request,response);var csrfRepository=new HttpSessionCsrfTokenRepository();new CsrfAuthenticationStrategy(csrfRepository).onAuthentication(auth,request,response);
 // Materialize a fresh token before returning: parallel first requests must share one session token.
 csrfRepository.saveToken(csrfRepository.generateToken(request),request,response);var context=SecurityContextHolder.createEmptyContext();context.setAuthentication(auth);SecurityContextHolder.setContext(context);new HttpSessionSecurityContextRepository().saveContext(context,request,response);}
 public static void logout(HttpServletRequest request){var session=request.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();}
}
