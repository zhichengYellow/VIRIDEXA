import java.sql.*;import java.net.*;import java.util.*;import java.util.concurrent.*;
import org.viridexa.api.account.*;
import org.springframework.dao.DuplicateKeyException;
/** Actual TiDB transaction tests in a new disposable schema, never the release dataset. */
class CloudAccountContract {
 static void exec(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){s.execute(sql);}}
 static long count(Connection c,String table)throws Exception{return Long.parseLong(TiDBReleaseTool.scalar(c,"SELECT COUNT(*) FROM "+table));}
 static String ddl(Connection c,String table)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery("SHOW CREATE TABLE "+table)){r.next();return r.getString(2);}}
 public static void main(String[] args){try{run();}catch(Exception e){System.err.println("Cloud account contract failed: "+e.getClass().getSimpleName()+(e instanceof SQLException s?" / code="+s.getErrorCode():""));System.exit(1);}}
 static void run()throws Exception{
  String url=TiDBReleaseTool.env("TIDB_JDBC_URL");TiDBReleaseTool.validateTarget("verify",url);
  String source=URI.create(url.substring(5)).getPath().substring(1),suffix=UUID.randomUUID().toString().replace("-","").substring(0,8);
  String schema="viridexa_tidb_test_account_"+suffix;
  String root=TiDBReleaseTool.env("TIDB_MIGRATOR_USER"),secret=TiDBReleaseTool.env("TIDB_MIGRATOR_PASSWORD"),user=root.split("\\.",2)[0]+".vct_"+suffix,password=UUID.randomUUID().toString()+UUID.randomUUID();
  String testUrl=url.replace("/"+source+"?","/"+schema+"?");boolean created=false,userCreated=false;
  try(var admin=DriverManager.getConnection(url,root,secret)){
   try{
    exec(admin,"CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");created=true;
    List<String> tables=List.of("app_role","app_user","user_role","public_member_account");var definitions=new ArrayList<String>();for(String table:tables)definitions.add(ddl(admin,table));
    exec(admin,"USE `"+schema+"`");for(String sql:definitions)exec(admin,sql);
    exec(admin,"CREATE TABLE scientific_candidate(id INT PRIMARY KEY,reason TEXT)");
    exec(admin,"INSERT INTO app_role(role_key,description) VALUES('MEMBER','Isolated fixed membership'),('ADMIN','Isolated forbidden role')");
    exec(admin,"CREATE USER '"+user+"'@'%' IDENTIFIED BY '"+password+"'");userCreated=true;
    for(String table:tables)exec(admin,"GRANT SELECT ON `"+schema+"`."+table+" TO '"+user+"'@'%'");
    for(String table:List.of("app_user","public_member_account"))exec(admin,"GRANT INSERT ON `"+schema+"`."+table+" TO '"+user+"'@'%'");
    var store=new AccountStore(testUrl,user,password,"portable");
    try{
     store.register("contract1@example.invalid","Isolated technical fixture","not-a-production-login-hash");
     if(count(admin,"app_user")!=1||count(admin,"public_member_account")!=1||count(admin,"user_role")!=0)throw new IllegalStateException("Incomplete enrollment");
     Long id=store.jdbc().queryForObject("SELECT id FROM app_user WHERE email=?",Long.class,"contract1@example.invalid");
     if(!AccountRoles.read(store.jdbc(),id,true).equals(List.of("MEMBER")))throw new IllegalStateException("Unexpected roles");
     boolean duplicate=false;try{store.register("contract1@example.invalid","fixture","fixture");}catch(DuplicateKeyException expected){duplicate=true;}if(!duplicate||count(admin,"app_user")!=1)throw new IllegalStateException("Duplicate contract failed");
     exec(admin,"REVOKE INSERT ON `"+schema+"`.public_member_account FROM '"+user+"'@'%'");
     boolean rollback=false;try{store.register("rollback@example.invalid","fixture","fixture");}catch(org.springframework.dao.DataAccessException expected){rollback=true;}
     if(!rollback||count(admin,"app_user")!=1)throw new IllegalStateException("Account rollback failed");
     exec(admin,"GRANT INSERT ON `"+schema+"`.public_member_account TO '"+user+"'@'%'");
     var executor=Executors.newFixedThreadPool(2);try{
      var tasks=List.of((Callable<String>)()->{try{store.register("concurrent@example.invalid","fixture","fixture");return "inserted";}catch(DuplicateKeyException e){return "duplicate";}},()->{try{store.register("concurrent@example.invalid","fixture","fixture");return "inserted";}catch(DuplicateKeyException e){return "duplicate";}});
      var outcomes=new ArrayList<String>();for(var future:executor.invokeAll(tasks))outcomes.add(future.get());Collections.sort(outcomes);
      if(!outcomes.equals(List.of("duplicate","inserted"))||count(admin,"app_user")!=2||count(admin,"public_member_account")!=2)throw new IllegalStateException("Concurrency contract failed");
     }finally{executor.shutdownNow();}
     System.out.println("Actual isolated TiDB: fixed MEMBER enrollment, duplicate rejection, transaction rollback and concurrent uniqueness passed. Production user writes=0.");
    }finally{store.close();}
   }finally{
    exec(admin,"USE `"+source+"`");
    if(created)exec(admin,"DROP DATABASE `"+schema+"`");if(userCreated)exec(admin,"DROP USER '"+user+"'@'%'");
    System.out.println("Owned cloud account fixtures and SQL identity removed.");
   }
  }
 }
}
