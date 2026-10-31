import java.sql.*;import java.nio.file.*;import java.net.*;import java.util.*;
/** Own-project technical SQL identities only; never application administrator accounts. */
class RuntimeProvision {
 static void exec(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){s.execute(sql);}}
 static void denied(Connection c,String sql)throws Exception{try{exec(c,sql);throw new IllegalStateException("Permission boundary failed");}catch(SQLException e){if(!Set.of(1142,1143,1044,1227).contains(e.getErrorCode())&&!(e.getErrorCode()==8121&&e.getMessage().startsWith("privilege check for")))throw e;}}
 public static void main(String[] args){try{run(args);}catch(Exception e){String detail=Objects.requireNonNullElse(e.getMessage(),"");for(var entry:System.getenv().entrySet())if(entry.getKey().endsWith("PASSWORD")&&!entry.getValue().isEmpty())detail=detail.replace(entry.getValue(),"<redacted>");System.err.println("Provision diagnostic: "+detail.substring(0,Math.min(detail.length(),600)));System.err.println("Provision stopped: "+e.getClass().getSimpleName()+(e instanceof SQLException s?" / SQLState="+s.getSQLState()+" / code="+s.getErrorCode():""));System.exit(1);}}
 static void run(String[] args)throws Exception{
  String url=TiDBReleaseTool.env("TIDB_JDBC_URL");TiDBReleaseTool.validateTarget("migrate",url);
  String schema=URI.create(url.substring(5)).getPath().substring(1);
  String prefix=TiDBReleaseTool.env("TIDB_MIGRATOR_USER").split("\\.",2)[0];if(!prefix.matches("[a-zA-Z0-9]+"))throw new IllegalArgumentException();
  String grants=Files.readString(Path.of(args[0])).replace("viridexa_tidb_20261031",schema);
  Map<String,String> users=Map.of("viridexa_ro","DB_USERNAME","viridexa_members","DB_ACCOUNT_USERNAME","viridexa_reviewer","DB_REVIEW_USERNAME");
  try(var c=DriverManager.getConnection(url,TiDBReleaseTool.env("TIDB_MIGRATOR_USER"),TiDBReleaseTool.env("TIDB_MIGRATOR_PASSWORD"))){
   for(var entry:users.entrySet()){
    String user=TiDBReleaseTool.env(entry.getValue()),password=TiDBReleaseTool.env(entry.getValue().replace("USERNAME","PASSWORD"));
    if(!user.startsWith(prefix+".vx_")||!user.matches("[a-zA-Z0-9_.]+")||!password.matches("[a-zA-Z0-9_-]{40,}"))throw new IllegalArgumentException("Unexpected generated identity");
    exec(c,"CREATE USER IF NOT EXISTS '"+user+"'@'%' IDENTIFIED BY '"+password+"'");grants=grants.replace("'"+entry.getKey()+"'","'"+user+"'");
   }
   grants=grants.replaceAll("(?m)^--.*$","");
   for(String sql:grants.split(";"))if(!sql.isBlank()){if(!sql.trim().startsWith("GRANT ")||!sql.contains(" ON "+schema+"."))throw new IllegalArgumentException("Outside owned schema");exec(c,sql);}
  }
  for(var entry:users.entrySet()){
   try(var c=DriverManager.getConnection(url,TiDBReleaseTool.env(entry.getValue()),TiDBReleaseTool.env(entry.getValue().replace("USERNAME","PASSWORD")))){
    TiDBReleaseTool.scalar(c,"SELECT COUNT(*) FROM app_role");
    denied(c,"UPDATE app_role SET description=description WHERE 1=0");
    denied(c,"INSERT INTO user_role(user_id,role_id) VALUES(0,0)");
    denied(c,"UPDATE scientific_candidate SET reason=reason WHERE 1=0");
    if(entry.getKey().equals("viridexa_ro")){
     denied(c,"INSERT INTO app_user(email,display_name,password_hash) VALUES('fixture-invalid','fixture','fixture')");
     try(var s=c.createStatement();var r=s.executeQuery("SHOW GRANTS")){while(r.next()){
      String g=r.getString(1).toUpperCase(Locale.ROOT);
      if(!g.split(" ON ",2)[0].matches("GRANT (USAGE|SELECT)")||g.contains("WITH GRANT OPTION"))throw new IllegalStateException("Public writer detected");
     }}
    }
   }
  }
  System.out.println("Three project-only SQL identities provisioned; readonly, role-write and raw-candidate-write boundaries passed. No human administrator created.");
 }
}
