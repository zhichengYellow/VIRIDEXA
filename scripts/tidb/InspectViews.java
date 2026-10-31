import java.sql.*;import java.nio.file.*;import java.util.regex.*;
/** Read-only diagnosis of the first uncreated release view after a DDL failure. */
class InspectViews {
 public static void main(String[] args)throws Exception{
  String url=TiDBReleaseTool.env("TIDB_JDBC_URL");TiDBReleaseTool.validateTarget("verify",url);
  try(var c=DriverManager.getConnection(url,TiDBReleaseTool.env("TIDB_MIGRATOR_USER"),TiDBReleaseTool.env("TIDB_MIGRATOR_PASSWORD"))){
   // Query-only action; TiDB does not implement JDBC session READ ONLY.
   for(String ddl:Files.readString(Path.of(args[0])).split(";")){
    var matcher=Pattern.compile("(?is)^\\s*CREATE(?: SQL SECURITY DEFINER)? VIEW `?(\\w+)`? AS\\s+(.+)$").matcher(ddl);
    if(!matcher.matches())continue;
    String name=matcher.group(1);
    try(var s=c.prepareStatement("SELECT COUNT(*) FROM information_schema.views WHERE table_schema=DATABASE() AND table_name=?")){
     s.setString(1,name);try(var r=s.executeQuery()){r.next();if(r.getInt(1)>0){System.out.println(name+": exists");continue;}}
    }
    try(var s=c.createStatement()){s.setQueryTimeout(20);try(var r=s.executeQuery("EXPLAIN "+matcher.group(2))){System.out.println(name+": query plans successfully");}}
    catch(SQLException e){System.out.println(name+": code="+e.getErrorCode()+" / "+e.getMessage());return;}
   }
  }
 }
}
