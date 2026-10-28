package org.viridexa.api.protection;
import java.io.IOException;import java.util.*;
import jakarta.servlet.*;import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
/** Nginx overwrites these private headers. Trust only configured immediate peers, never arbitrary XFF. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE+5)
public class ClientIpFilter extends OncePerRequestFilter {
 private final List<IpAddressMatcher> trusted;
 public ClientIpFilter(@Value("${viridexa.security.trusted-proxy-cidrs:}")String cidrs){trusted=Arrays.stream(cidrs.split(",")).filter(x->!x.isBlank()).map(String::trim).map(ClientIpFilter::narrowProxy).toList();}
 private static IpAddressMatcher narrowProxy(String cidr){String[] parts=cidr.split("/",-1);if(parts.length>2||!literal(parts[0]))throw new IllegalArgumentException("Trusted proxy must be a literal IP CIDR");int bits=parts[0].contains(":")?128:32;int prefix=parts.length==1?bits:Integer.parseInt(parts[1]);if(prefix<(bits==32?24:64)||prefix>bits)throw new IllegalArgumentException("Trusted proxy CIDR is too broad");return new IpAddressMatcher(cidr);}
 public boolean configured(){return !trusted.isEmpty();}
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse response,FilterChain chain)throws ServletException,IOException{
  String ip=req.getHeader("X-Viridexa-Client-IP");boolean peer=trusted.stream().anyMatch(t->t.matches(req.getRemoteAddr()));
  if(peer&&ip!=null&&literal(ip)){
   final boolean secure="https".equals(req.getHeader("X-Viridexa-Proto"));final String visitor=ip;
   chain.doFilter(new HttpServletRequestWrapper(req){@Override public String getRemoteAddr(){return visitor;}@Override public String getRemoteHost(){return visitor;}@Override public String getScheme(){return secure?"https":"http";}@Override public boolean isSecure(){return secure;}},response);
  }else chain.doFilter(req,response);
 }
 static boolean literal(String ip){if(ip.length()>45||!ip.matches("[0-9a-fA-F:.]+"))return false;try{java.net.InetAddress.getByName(ip);return true;}catch(Exception invalid){return false;}}
}
