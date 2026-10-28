package org.viridexa.api.account;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DuplicateKeyException;
@Service
public class AccountService {
 public record Profile(long id,String email,String displayName,List<String> roles,String emailVerification){}
 private final AccountStore store;private final PasswordEncoder encoder;
 public AccountService(AccountStore store,PasswordEncoder encoder){this.store=store;this.encoder=encoder;}
 public Profile register(String email,String displayName,String password){AccountRules.password(password);String name=AccountRules.name(displayName);try{store.jdbc().queryForObject("CALL register_public_member(?,?,?)",Long.class,email,name,encoder.encode(password));}catch(DuplicateKeyException e){throw new ResponseStatusException(HttpStatus.CONFLICT,"注册未完成，请更换邮箱或前往登录");}return profile(email);}
 public Profile profile(String email){var rows=store.jdbc().query("SELECT id,email,display_name FROM app_user WHERE email=? AND enabled=TRUE",(r,n)->new Object[]{r.getLong(1),r.getString(2),r.getString(3)},email);if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"请重新登录");var u=rows.getFirst();var roles=store.jdbc().queryForList("SELECT r.role_key FROM app_role r JOIN user_role ur ON ur.role_id=r.id WHERE ur.user_id=? ORDER BY r.role_key",String.class,u[0]);return new Profile((Long)u[0],(String)u[1],(String)u[2],roles,"NOT_CONFIGURED");}
}
