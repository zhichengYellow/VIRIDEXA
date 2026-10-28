package org.viridexa.api.governance;

import com.zaxxer.hikari.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.annotation.PreDestroy;

/** Separate pool, never registered as the public application's DataSource. */
@Component
public class ReviewStore {
 private final HikariDataSource dataSource;
 private final JdbcTemplate jdbc;
 private final TransactionTemplate transaction;
 public ReviewStore(@Value("${spring.datasource.url}") String url,
   @Value("${viridexa.review.username:viridexa_reviewer}") String username,
   @Value("${viridexa.review.password:}") String password) {
  var config=new HikariConfig();config.setJdbcUrl(url);config.setUsername(username);config.setPassword(password);
  config.setPoolName("scientific-review-writer");config.setMaximumPoolSize(3);config.setMinimumIdle(0);
  config.setInitializationFailTimeout(-1);config.setConnectionTimeout(5000);
  dataSource=new HikariDataSource(config);jdbc=new JdbcTemplate(dataSource);jdbc.setQueryTimeout(8);
  transaction=new TransactionTemplate(new DataSourceTransactionManager(dataSource));
 }
 public JdbcTemplate jdbc() { return jdbc; }
 public TransactionTemplate transaction() { return transaction; }
 @PreDestroy public void close() { dataSource.close(); }
}
