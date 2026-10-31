package org.viridexa.api.account;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
/** Enrollment always means MEMBER. The account DB identity cannot write user_role. */
public final class AccountRoles {
 private AccountRoles() {}
 public static List<String> read(JdbcTemplate jdbc,Object userId,boolean portable) {
  String base="SELECT r.role_key FROM app_role r JOIN user_role ur ON ur.role_id=r.id WHERE ur.user_id=?";
  return portable ? jdbc.queryForList(base+" UNION SELECT r.role_key FROM public_member_account p JOIN app_role r ON r.role_key='MEMBER' WHERE p.user_id=? ORDER BY role_key",String.class,userId,userId)
   : jdbc.queryForList(base+" ORDER BY r.role_key",String.class,userId);
 }
}
