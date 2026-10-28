package org.viridexa.api.governance;
import java.io.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
/** One-shot local bootstrap helper; never registered as an API or application bean. */
public final class PasswordHash {
 public static void main(String[] args) throws Exception {
  String password=new BufferedReader(new InputStreamReader(System.in)).readLine();
  if(password==null||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length<12||password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("Password must contain 12 to 72 UTF-8 bytes");
  System.out.print(new BCryptPasswordEncoder(12).encode(password));
 }
}
