package org.viridexa.api.account;
import com.zaxxer.hikari.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.annotation.PreDestroy;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import java.sql.Statement;
import org.springframework.dao.DataIntegrityViolationException;
/** Separate identity: fixed MySQL procedure, or insert-only portable enrollment. Never user_role DML. */
@Component
public class AccountStore {
 private final HikariDataSource pool;private final JdbcTemplate jdbc;private final TransactionTemplate transaction;private final boolean portable;
 public AccountStore(@Value("${spring.datasource.url}")String url,@Value("${viridexa.accounts.username:viridexa_accounts}")String user,@Value("${viridexa.accounts.password:}")String password,@Value("${viridexa.accounts.registration-mode:procedure}")String mode){if(!java.util.Set.of("procedure","portable").contains(mode))throw new IllegalArgumentException("Unknown account registration mode");portable=mode.equals("portable");var c=new HikariConfig();c.setJdbcUrl(url);c.setUsername(user);c.setPassword(password);c.setPoolName("public-account-writer");c.setMaximumPoolSize(2);c.setMinimumIdle(0);c.setInitializationFailTimeout(-1);c.setConnectionTimeout(5000);pool=new HikariDataSource(c);jdbc=new JdbcTemplate(pool);jdbc.setQueryTimeout(8);transaction=new TransactionTemplate(new DataSourceTransactionManager(pool));}
 public void register(String email,String name,String passwordHash){
  if(!portable){jdbc.queryForObject("CALL register_public_member(?,?,?)",Long.class,email,name,passwordHash);return;}
  transaction.executeWithoutResult(status->{
   Integer member=jdbc.queryForObject("SELECT COUNT(*) FROM app_role WHERE role_key='MEMBER'",Integer.class);
   if(member==null||member!=1)throw new DataIntegrityViolationException("Public membership unavailable");
   var key=new GeneratedKeyHolder();
   jdbc.update(connection->{var statement=connection.prepareStatement("INSERT INTO app_user(email,display_name,password_hash) VALUES(?,?,?)",Statement.RETURN_GENERATED_KEYS);statement.setString(1,email);statement.setString(2,name);statement.setString(3,passwordHash);return statement;},key);
   if(key.getKey()==null)throw new DataIntegrityViolationException("Missing generated account ID");
   long id=key.getKey().longValue();
   if(jdbc.update("INSERT INTO public_member_account(user_id) VALUES(?)",id)!=1)throw new DataIntegrityViolationException("Public enrollment unavailable");
  });
 }
 public boolean portable(){return portable;}
 public JdbcTemplate jdbc(){return jdbc;}
 @PreDestroy public void close(){pool.close();}
}
