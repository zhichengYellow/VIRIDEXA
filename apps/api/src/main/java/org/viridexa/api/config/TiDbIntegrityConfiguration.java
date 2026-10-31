package org.viridexa.api.config;
import java.util.*;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.SmartInitializingSingleton;
/** A compatibility profile must not silently disable integrity or use a writer for public queries. */
@Configuration @Profile("tidb")
public class TiDbIntegrityConfiguration {
 @Bean SmartInitializingSingleton tidbSafetyGate(JdbcTemplate jdbc){return ()->{
  for(String flag:List.of("foreign_key_checks","tidb_enable_foreign_key","tidb_enable_check_constraint")){
   String value=jdbc.queryForObject("SELECT @@"+flag,String.class);
   if(value==null||!Set.of("ON","1").contains(value.toUpperCase(Locale.ROOT)))throw new IllegalStateException("TiDB integrity feature unavailable: "+flag);
  }
  var grants=jdbc.queryForList("SHOW GRANTS",String.class);
  for(String grant:grants){String privileges=grant.toUpperCase(Locale.ROOT).split(" ON ",2)[0];
   if(!privileges.matches("GRANT (USAGE|SELECT)")||grant.toUpperCase(Locale.ROOT).contains("WITH GRANT OPTION"))throw new IllegalStateException("TiDB public datasource requires direct SELECT-only grants without delegation");
  }
 };}
}
