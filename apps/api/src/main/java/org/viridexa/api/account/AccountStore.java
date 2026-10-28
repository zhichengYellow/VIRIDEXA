package org.viridexa.api.account;
import com.zaxxer.hikari.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import jakarta.annotation.PreDestroy;
/** Only SELECT on identity tables and EXECUTE on fixed member registration. No review writes. */
@Component
public class AccountStore {
 private final HikariDataSource pool;private final JdbcTemplate jdbc;
 public AccountStore(@Value("${spring.datasource.url}")String url,@Value("${viridexa.accounts.username:viridexa_accounts}")String user,@Value("${viridexa.accounts.password:}")String password){var c=new HikariConfig();c.setJdbcUrl(url);c.setUsername(user);c.setPassword(password);c.setPoolName("public-account-writer");c.setMaximumPoolSize(2);c.setMinimumIdle(0);c.setInitializationFailTimeout(-1);c.setConnectionTimeout(5000);pool=new HikariDataSource(c);jdbc=new JdbcTemplate(pool);jdbc.setQueryTimeout(8);}
 public JdbcTemplate jdbc(){return jdbc;}
 @PreDestroy public void close(){pool.close();}
}
