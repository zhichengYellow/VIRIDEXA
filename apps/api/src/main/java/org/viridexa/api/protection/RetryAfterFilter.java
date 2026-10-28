package org.viridexa.api.protection;
import java.io.IOException;import jakarta.servlet.*;import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;import org.springframework.web.filter.OncePerRequestFilter;
@Component
public class RetryAfterFilter extends OncePerRequestFilter {
 @Override protected void doFilterInternal(HttpServletRequest q,HttpServletResponse r,FilterChain c)throws IOException,ServletException{c.doFilter(q,new HttpServletResponseWrapper(r){private void retry(int s){if(s==429&&!containsHeader("Retry-After"))setHeader("Retry-After","60");} @Override public void setStatus(int s){retry(s);super.setStatus(s);}@Override public void sendError(int s)throws IOException{retry(s);super.sendError(s);}@Override public void sendError(int s,String m)throws IOException{retry(s);super.sendError(s,m);}});}
}
