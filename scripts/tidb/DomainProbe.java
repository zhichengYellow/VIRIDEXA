import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.datasource.DriverManagerDataSource;import org.viridexa.api.graph.GraphService;
/** Actual read-only domain query diagnostics; no raw result values or SQL credentials printed. */
class DomainProbe {
 public static void main(String[] args){
  var ds=new DriverManagerDataSource(TiDBReleaseTool.env("DB_URL"),TiDBReleaseTool.env("DB_USERNAME"),TiDBReleaseTool.env("DB_PASSWORD"));
  var jdbc=new JdbcTemplate(ds){
   int number=0;
   @Override public java.util.List<java.util.Map<String,Object>> queryForList(String sql,Object... values){
    int id=++number;long start=System.nanoTime();try{return super.queryForList(sql,values);}finally{System.out.println("Query "+id+" ms="+((System.nanoTime()-start)/1000000)+" SQL chars="+sql.length());}
   }
  };jdbc.setQueryTimeout(10);
  try{var result=new GraphService(jdbc).neighbors("plant:2",null,null,null,5,null,1);System.out.println("Graph nodes="+result.nodes().size()+", edges="+result.edges().size());}
  catch(Exception e){for(Throwable cause=e;cause!=null;cause=cause.getCause())if(cause instanceof java.sql.SQLException sql)System.out.println("Graph SQL diagnostic: code="+sql.getErrorCode()+" / "+sql.getMessage());System.exit(1);}
 }
}
