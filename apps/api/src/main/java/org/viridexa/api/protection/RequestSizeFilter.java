package org.viridexa.api.protection;
import java.io.*;import jakarta.servlet.*;import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;import org.springframework.core.annotation.Order;import org.springframework.core.Ordered;import org.springframework.web.filter.OncePerRequestFilter;
/** All API operations use bounded JSON, no upload route exists. Also bounds chunked bodies. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE+10)
public class RequestSizeFilter extends OncePerRequestFilter {
 static final int MAX=32768;
 @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)throws IOException,ServletException{
  if(!req.getRequestURI().startsWith("/api/")||!(req.getMethod().equals("POST")||req.getMethod().equals("PUT")||req.getMethod().equals("PATCH"))){chain.doFilter(req,res);return;}
  if(req.getContentLengthLong()>MAX){reject(res,413,"REQUEST_TOO_LARGE","请求内容过大，请缩小范围后重试。");return;}
  if(req.getContentType()!=null&&!req.getContentType().toLowerCase(java.util.Locale.ROOT).split(";",2)[0].strip().equals("application/json")){reject(res,415,"UNSUPPORTED_CONTENT_TYPE","此接口仅接受 JSON，请勿上传文件。");return;}
  byte[] body=req.getInputStream().readNBytes(MAX+1);if(body.length>MAX){reject(res,413,"REQUEST_TOO_LARGE","请求内容过大，请缩小范围后重试。");return;}
  chain.doFilter(new HttpServletRequestWrapper(req){@Override public ServletInputStream getInputStream(){var in=new ByteArrayInputStream(body);return new ServletInputStream(){public int read(){return in.read();}public boolean isFinished(){return in.available()==0;}public boolean isReady(){return true;}public void setReadListener(ReadListener l){throw new UnsupportedOperationException();}};}@Override public BufferedReader getReader(){return new BufferedReader(new InputStreamReader(getInputStream(),java.nio.charset.StandardCharsets.UTF_8));}},res);
 }
 private void reject(HttpServletResponse r,int status,String code,String message)throws IOException{r.setStatus(status);r.setContentType("application/json;charset=UTF-8");r.setHeader("Cache-Control","no-store");r.getWriter().write("{\"code\":\""+code+"\",\"message\":\""+message+"\"}");}
}
