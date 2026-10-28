package org.viridexa.api.account;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
public final class AccountRules {
 private AccountRules(){}
 public static String email(String value){return value.strip().toLowerCase(Locale.ROOT);}
 public static void password(String value){int bytes=value.getBytes(StandardCharsets.UTF_8).length;if(bytes<12||bytes>72||value.codePoints().anyMatch(Character::isISOControl))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"密码需为12–72个UTF-8字节，不含控制字符");}
 public static String name(String value){String n=value.strip();if(n.length()<2||n.length()>60||n.codePoints().anyMatch(Character::isISOControl))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"昵称格式无效");return n;}
}
