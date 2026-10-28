package org.viridexa.api.governance;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EvidenceIntegrity {
 private final Path root;
 private record Checked(long size,long modified,String sha){}
 private final ConcurrentHashMap<Path,Checked> checked=new ConcurrentHashMap<>();
 public EvidenceIntegrity(@Value("${viridexa.protected-root:}") String configured){root=configured.isBlank()?null:Path.of(configured);}
 public Path verify(String relative,String expected){
  try{
   if(root==null) {ReviewRules.invalid("原始证据只读目录尚未配置");}
   Path base=root.toRealPath();Path input=base.resolve(relative).normalize().toRealPath();
   if(!input.startsWith(base)||!Files.isRegularFile(input))ReviewRules.invalid("证据路径不合法");
   long size=Files.size(input),modified=Files.getLastModifiedTime(input).toMillis();
   Checked old=checked.get(input);
   if(old==null||old.size()!=size||old.modified()!=modified){
    MessageDigest hash=MessageDigest.getInstance("SHA-256");try(var stream=Files.newInputStream(input)){byte[] buf=new byte[1024*1024];int n;while((n=stream.read(buf))!=-1)hash.update(buf,0,n);}
    if(size!=Files.size(input)||modified!=Files.getLastModifiedTime(input).toMillis())ReviewRules.invalid("核验过程中来源文件发生变化");
    old=new Checked(size,modified,HexFormat.of().formatHex(hash.digest()));checked.put(input,old);
   }
   if(!old.sha().equals(expected))ReviewRules.invalid("原始来源文件 SHA-256 不匹配");return input;
  }catch(org.springframework.web.server.ResponseStatusException e){throw e;}
  catch(Exception e){ReviewRules.invalid("原始证据文件不可访问");return null;}
 }
}
