package org.viridexa.api.protection;
import jakarta.servlet.*;import jakarta.servlet.http.*;import java.io.IOException;
import org.springframework.stereotype.Component;import org.springframework.web.filter.OncePerRequestFilter;
/** Input resource bounds supplement domain-specific filters; unknown scientific values stay unknown. */
@Component
public class QueryBoundsFilter extends OncePerRequestFilter {
 @Override protected void doFilterInternal(HttpServletRequest q,HttpServletResponse r,FilterChain chain)throws IOException,ServletException{
  if(q.getMethod().equals("GET")&&q.getRequestURI().startsWith("/api/")){
   int length=0,count=0;boolean invalid=false;
   for(var e:q.getParameterMap().entrySet()){count++;if(e.getKey().length()>60){invalid=true;break;}for(String v:e.getValue()){length+=v.length();int max=e.getKey().equals("cursor")?2048:e.getKey().equals("q")||e.getKey().equals("query")?120:512;if(v.length()>max||e.getValue().length>8)invalid=true;if(e.getKey().equals("limit")){try{int limit=Integer.parseInt(v);if(limit<1||limit>100)invalid=true;}catch(NumberFormatException bad){invalid=true;}}}}
   if(invalid||count>24||length>4096){r.setStatus(400);r.setContentType("application/json;charset=UTF-8");r.getWriter().write("{\"code\":\"QUERY_BOUNDS\",\"message\":\"查询条件过长或分页超限，请缩小范围后重试。\"}");return;}
  }chain.doFilter(q,r);
 }
}
