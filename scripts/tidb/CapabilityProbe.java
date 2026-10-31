import java.sql.*;import java.util.*;import java.net.URI;
/** Bounded cloud capability experiment, no source data, never imports into sys. */
class CapabilityProbe {
 static void exec(Connection c,String sql)throws SQLException{try(var s=c.createStatement()){s.setQueryTimeout(30);s.execute(sql);}}
 static String result(SQLException e){return "SQLState="+e.getSQLState()+", code="+e.getErrorCode();}
 public static void main(String[] args)throws Exception{
  String url=TiDBReleaseTool.env("TIDB_JDBC_URL");TiDBReleaseTool.validateTarget("inspect-target",url);
  String suffix=UUID.randomUUID().toString().replace("-","").substring(0,12);
  String prefix=TiDBReleaseTool.env("TIDB_MIGRATOR_USER").split("\\.",2)[0];
  if(!prefix.matches("[a-zA-Z0-9]+"))throw new IllegalArgumentException("Invalid tenant prefix");
  String db="viridexa_tidb_probe_"+suffix,user=prefix+".vp_"+suffix.substring(0,8);
  boolean dbCreated=false,userCreated=false;String password=UUID.randomUUID().toString()+UUID.randomUUID();
  try(var c=DriverManager.getConnection(url,TiDBReleaseTool.env("TIDB_MIGRATOR_USER"),TiDBReleaseTool.env("TIDB_MIGRATOR_PASSWORD"))){
   System.out.println("Server="+TiDBReleaseTool.scalar(c,"SELECT VERSION()"));
   // Only enables integrity; never disables any flag or modifies existing data.
   if(!Set.of("1","ON").contains(TiDBReleaseTool.scalar(c,"SELECT @@tidb_enable_check_constraint").toUpperCase(Locale.ROOT))){
    try{exec(c,"SET GLOBAL tidb_enable_check_constraint=ON");System.out.println("Enable CHECK: accepted");}
    catch(SQLException e){System.out.println("Enable CHECK: rejected / "+result(e));}
   }
   System.out.println("CHECK flag="+TiDBReleaseTool.scalar(c,"SELECT @@tidb_enable_check_constraint"));
   exec(c,"CREATE DATABASE `"+db+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");dbCreated=true;
   exec(c,"CREATE TABLE `"+db+"`.parent(id INT PRIMARY KEY AUTO_INCREMENT, value INT NOT NULL, CONSTRAINT ck_positive CHECK(value>0))");
   exec(c,"CREATE TABLE `"+db+"`.child(id INT PRIMARY KEY,parent_id INT,FOREIGN KEY(parent_id) REFERENCES `"+db+"`.parent(id))");
   try{exec(c,"INSERT INTO `"+db+"`.parent VALUES(1,-1)");throw new IllegalStateException("CHECK not enforced");}catch(SQLException e){if(e.getErrorCode()!=3819)throw e;System.out.println("CHECK rejection: "+result(e));}
   try{exec(c,"INSERT INTO `"+db+"`.child VALUES(1,999)");throw new IllegalStateException("Foreign key not enforced");}catch(SQLException e){if(e.getErrorCode()!=1452)throw e;System.out.println("Foreign-key rejection: "+result(e));}
   try{
    exec(c,"CREATE USER '"+user+"'@'%' IDENTIFIED BY '"+password+"'");userCreated=true;
    exec(c,"GRANT SELECT ON `"+db+"`.parent TO '"+user+"'@'%'");System.out.println("Table grants: accepted");
    try{exec(c,"GRANT INSERT(value) ON `"+db+"`.parent TO '"+user+"'@'%'");System.out.println("Column grants: accepted");
     try(var restricted=DriverManager.getConnection(url.replace(URI.create(url.substring(5)).getPath()+"?","/"+db+"?"),user,password)){
      try(var s=c.createStatement();var r=s.executeQuery("SHOW GRANTS FOR '"+user+"'@'%'")){while(r.next())System.out.println("Probe grants="+r.getString(1).replace(user,"<probe>").replace(db,"<probe_schema>"));}
      try{exec(restricted,"INSERT INTO parent(value) VALUES(1)");}catch(SQLException e){System.out.println("Allowed INSERT failure="+e.getMessage().replace(user,"<probe>").replace(db,"<probe_schema>"));throw e;}System.out.println("Allowed column insert: passed");
      try{exec(restricted,"INSERT INTO parent(id,value) VALUES(100,1)");throw new IllegalStateException("Column restriction NOT enforced");}
      catch(SQLException e){if(e.getErrorCode()!=1143&&e.getErrorCode()!=1142)throw e;System.out.println("Unpermitted column insert: denied / "+result(e));}
      try{exec(restricted,"UPDATE parent SET value=2");throw new IllegalStateException("Write restriction NOT enforced");}
      catch(SQLException e){if(e.getErrorCode()!=1143&&e.getErrorCode()!=1142)throw e;System.out.println("Unpermitted update: denied / "+result(e));}
     }}
    catch(SQLException e){System.out.println("Column grants: rejected / "+result(e));}
   }catch(SQLException e){System.out.println("Technical user creation: rejected / "+result(e));}
  }finally{
   try(var c=DriverManager.getConnection(url,TiDBReleaseTool.env("TIDB_MIGRATOR_USER"),TiDBReleaseTool.env("TIDB_MIGRATOR_PASSWORD"))){
    if(dbCreated){exec(c,"DROP DATABASE `"+db+"`");System.out.println("Owned disposable probe schema removed");}
    if(userCreated){exec(c,"DROP USER '"+user+"'@'%'");System.out.println("Owned disposable SQL identity removed");}
   }
  }
 }
}
