import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.*;
import java.net.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import java.io.*;
import org.flywaydb.core.Flyway;
import tools.jackson.databind.json.JsonMapper;

/** Deliberately accepts only a separate TiDB Cloud schema, never the source MySQL. */
class TiDBReleaseTool {
 static class GateFailure extends IllegalStateException { GateFailure(String code){super(code);} }
 static final JsonMapper JSON=JsonMapper.builder().build();
 static String env(String key){String value=System.getenv(key);if(value==null||value.isBlank())throw new GateFailure("Missing "+key);return value;}
 static String scalar(Connection c,String sql)throws SQLException{try(var s=c.createStatement();var r=s.executeQuery(sql)){if(!r.next())throw new SQLException("Missing scalar");return r.getString(1);}}
 static String hash(byte[] value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));}
 static void check(Connection c)throws Exception{
  String version=scalar(c,"SELECT VERSION()");var matcher=java.util.regex.Pattern.compile("TiDB-v(\\d+)\\.(\\d+)\\.(\\d+)").matcher(version);
  if(!matcher.find())throw new GateFailure("Not a recognized TiDB server");
  int major=Integer.parseInt(matcher.group(1)),minor=Integer.parseInt(matcher.group(2)),patch=Integer.parseInt(matcher.group(3));
  if(major<8||(major==8&&minor<5))throw new GateFailure("TiDB 8.5+ required");
  String permissions=Objects.requireNonNullElse(System.getenv("TIDB_PERMISSION_MODE"),"column-scoped");
  if(!Set.of("column-scoped","table-scoped").contains(permissions))throw new GateFailure("Unknown permission mode");
  if(major==8&&minor==5&&patch<6&&!permissions.equals("table-scoped"))throw new GateFailure("Older TiDB requires explicit table-scoped permission plan");
  for(String flag:List.of("foreign_key_checks","tidb_enable_foreign_key","tidb_enable_check_constraint")){
   if(!Set.of("1","ON").contains(scalar(c,"SELECT @@"+flag).toUpperCase(Locale.ROOT)))throw new GateFailure("Required integrity feature disabled: "+flag);
  }
  System.out.println("TiDB version and enabled integrity flags passed; no global settings changed.");
 }
 public static void main(String[] args){try{run(args);}catch(Exception error){
  // Never print SQL payloads, JDBC URLs or underlying messages containing credentials.
  System.err.println("TiDB operation stopped: "+error.getClass().getSimpleName()+(error instanceof GateFailure?" / "+error.getMessage():"")+(error instanceof SQLException sql?" / SQLState="+sql.getSQLState():""));System.exit(1);
 }}
 static void importData(Connection c,Path bundle,String bundleHash)throws Exception{
    c.setAutoCommit(false);
    try(var s=c.prepareStatement("SELECT manifest_sha256 FROM tidb_release_import_state WHERE id=1 FOR UPDATE");var r=s.executeQuery()){
     if(r.next()){if(!bundleHash.equals(r.getString(1)))throw new GateFailure("Different bundle already registered");}
     else{try(var insert=c.prepareStatement("INSERT INTO tidb_release_import_state(id,manifest_sha256) VALUES(1,?)")){insert.setString(1,bundleHash);insert.executeUpdate();}}
    }c.commit();
    Map<Integer,String> completed=new HashMap<>();
    try(var s=c.createStatement();var r=s.executeQuery("SELECT sequence_id,statement_sha256 FROM tidb_release_import_progress")){
     while(r.next())completed.put(r.getInt(1),r.getString(2));
    }c.commit();
    int sequence=0,applied=0,skipped=0;
    try(var in=new BufferedReader(new InputStreamReader(new GZIPInputStream(Files.newInputStream(bundle.resolve("business-data.sql.gz"))),StandardCharsets.UTF_8))){
     for(String line;(line=in.readLine())!=null;){if(line.isBlank())continue;
      if(!line.startsWith("INSERT INTO `")||!line.endsWith(";"))throw new GateFailure("Unexpected dump statement; no session/constraint changes allowed");
      sequence++;String sha=hash(line.getBytes(StandardCharsets.UTF_8));
      try{
       if(completed.containsKey(sequence)){
        if(!sha.equals(completed.get(sequence)))throw new GateFailure("Checkpoint mismatch");
        skipped++;continue;
       }
       try(var s=c.createStatement()){s.setQueryTimeout(120);s.executeUpdate(line);}
       try(var s=c.prepareStatement("INSERT INTO tidb_release_import_progress(sequence_id,statement_sha256) VALUES(?,?)")){s.setInt(1,sequence);s.setString(2,sha);s.executeUpdate();}
       c.commit();applied++;if(applied%25==0)System.out.println("Committed dump statements: "+applied);
      }catch(Exception e){c.rollback();throw e;}
     }
    }
    for(int number:completed.keySet())if(number<1||number>sequence)throw new GateFailure("Unexpected checkpoint sequence");
    System.out.println("Import completed: applied="+applied+", resumed="+skipped+". Run verification before enabling traffic.");

 }
 static void validateTarget(String action,String url){if(!url.startsWith("jdbc:mysql://"))throw new IllegalArgumentException("MySQL TLS URL required");
  URI address=URI.create(url.substring(5));String host=address.getHost(),schema=address.getPath().replaceFirst("^/","");
  if(host==null||!host.endsWith(".tidbcloud.com")||address.getUserInfo()!=null||!(schema.matches("viridexa_tidb_[a-z0-9_]+")||(action.equals("inspect-target")&&schema.equals("sys"))))throw new IllegalArgumentException("Separate TiDB Cloud release schema required");
  List<String> tls=new ArrayList<>();for(String q:Objects.requireNonNullElse(address.getRawQuery(),"").split("&")){String decoded=URLDecoder.decode(q,StandardCharsets.UTF_8);if(decoded.toLowerCase(Locale.ROOT).startsWith("sslmode="))tls.add(decoded);if(decoded.matches("(?i)(user|password|useSSL|verifyServerCertificate)=.*"))throw new IllegalArgumentException("Ambiguous JDBC parameters");}
  if(!tls.equals(List.of("sslMode=VERIFY_IDENTITY")))throw new IllegalArgumentException("Verified TLS required");
 }
 @SuppressWarnings("deprecation")
 static void run(String[] args)throws Exception{
  String url=env("TIDB_JDBC_URL");validateTarget(args[0],url);
  if(args[0].equals("create-target")){
   URI address=URI.create(url.substring(5));String schema=address.getPath().substring(1);
   String bootstrap=url.replace("/"+schema+"?","/sys?");
   try(var c=DriverManager.getConnection(bootstrap,env("TIDB_MIGRATOR_USER"),env("TIDB_MIGRATOR_PASSWORD"))){
    check(c);try(var s=c.createStatement()){s.execute("CREATE DATABASE `"+schema+"` CHARACTER SET utf8mb4 COLLATE utf8mb4_bin");}
    System.out.println("Fresh project database created; no existing schema overwritten.");return;
   }
  }
  try(var c=DriverManager.getConnection(url,env("TIDB_MIGRATOR_USER"),env("TIDB_MIGRATOR_PASSWORD"))){
   try(var session=c.createStatement()){session.execute("SET time_zone='+00:00'");}
   if(args[0].equals("inspect-target")){
    // Query-only action; TiDB does not implement JDBC session READ ONLY.
    System.out.println("version="+scalar(c,"SELECT VERSION()"));
    for(String flag:List.of("foreign_key_checks","tidb_enable_foreign_key","tidb_enable_check_constraint")){
     try{System.out.println(flag+"="+scalar(c,"SELECT @@"+flag));}
     catch(SQLException e){System.out.println(flag+"=UNAVAILABLE / SQLState="+e.getSQLState());}
    }
    check(c);return;
   }
   check(c);if(args[0].equals("preflight"))return;
   if(args[0].equals("status")){
    System.out.println("Server compression capability="+((c.unwrap(com.mysql.cj.jdbc.JdbcConnection.class).getSession().getServerSession().getCapabilities().getCapabilityFlags() & 32)!=0));
    System.out.println("Driver compression enabled="+c.unwrap(com.mysql.cj.jdbc.JdbcConnection.class).getPropertySet().getBooleanProperty(com.mysql.cj.conf.PropertyKey.useCompression).getValue());
    System.out.println("Committed import statements="+scalar(c,"SELECT COUNT(*) FROM tidb_release_import_progress"));
    System.out.println("Current Flyway version="+scalar(c,"SELECT MAX(version) FROM flyway_schema_history WHERE success=1"));return;
   }
   Path bundle=Path.of(args[1]);@SuppressWarnings("unchecked") Map<String,Object> manifest=JSON.readValue(Files.readString(bundle.resolve("manifest.json")),Map.class);
   String bundleHash=hash(Files.readAllBytes(bundle.resolve("manifest.json")));
   if(args[0].equals("migrate")){
    long tables=Long.parseLong(scalar(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE()"));
    boolean flyway="1".equals(scalar(c,"SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name='flyway_schema_history'"));
    if(tables!=0&&!flyway)throw new GateFailure("Target is not empty");
    Flyway.configure().dataSource(url,env("TIDB_MIGRATOR_USER"),env("TIDB_MIGRATOR_PASSWORD")).locations("filesystem:"+bundle.resolve("schema").toAbsolutePath()).load().migrate();
    System.out.println("TiDB baseline migrations completed; business data not imported.");return;
   }
   if(args[0].equals("import")){importData(c,bundle,bundleHash);return;}
   if(args[0].equals("verify")){
    @SuppressWarnings("unchecked") Map<String,Number> expected=(Map<String,Number>)manifest.get("tables");
    for(var table:expected.entrySet()){
     if(!table.getKey().matches("[a-zA-Z0-9_]+"))throw new IllegalArgumentException();
     if(Long.parseLong(scalar(c,"SELECT COUNT(*) FROM `"+table.getKey()+"`"))!=table.getValue().longValue())throw new GateFailure("Table row count mismatch");
    }
    String state=scalar(c,"SELECT JSON_ARRAY((SELECT COUNT(*) FROM scientific_candidate),(SELECT COALESCE(SUM(CRC32(CONCAT(id,content_sha256,normalization_state))),0) FROM scientific_candidate),(SELECT COUNT(*) FROM review_event),(SELECT COUNT(*) FROM source_record_access),(SELECT COALESCE(SUM(CRC32(CONCAT(candidate_id,visibility,content_sha256))),0) FROM source_record_access),(SELECT COALESCE(SUM(CRC32(projection_sha256)),0) FROM ontology_projection))");
    var actual=JSON.readTree(state);var original=JSON.readTree(manifest.get("sourceRevision").toString());
    if(actual.size()!=original.size())throw new GateFailure("State dimensions mismatch");
    for(int i=0;i<actual.size();i++)if(new java.math.BigDecimal(actual.get(i).asText()).compareTo(new java.math.BigDecimal(original.get(i).asText()))!=0)throw new GateFailure("Content/status fingerprint mismatch");
    var report=new LinkedHashMap<String,Object>();report.put("sourceTablesMatched",expected.size());report.put("sourceRowsMatched",expected.values().stream().mapToLong(Number::longValue).sum());
    var queries=new LinkedHashMap<String,String>();
    queries.put("publicPlants","SELECT COUNT(*) FROM graph_catalog WHERE is_public=1");
    queries.put("publicSourceRecords","SELECT COUNT(*) FROM public_source_record");
    queries.put("formalScientificFacts","SELECT COUNT(*) FROM published_scientific_fact");
    queries.put("scientificSamples","SELECT COUNT(*) FROM scientific_sample");
    queries.put("unlinkedSamples","SELECT COUNT(*) FROM scientific_sample WHERE linkage_state='UNLINKED'");
    queries.put("applicationUsers","SELECT COUNT(*) FROM app_user");
    queries.put("mediaRegistrations","SELECT COUNT(*) FROM botanical_media");
    queries.put("licenseUnknownSourceVersions","SELECT COUNT(*) FROM scientific_source_version WHERE license_state='UNKNOWN'");
    queries.put("publicChineseNamedPlants","SELECT COUNT(DISTINCT p.id) FROM graph_catalog p JOIN plant_name n ON n.plant_id=p.id WHERE p.is_public=1 AND n.language_tag LIKE 'zh%' AND n.name_role='COMMON' AND (n.review_state IN ('APPROVED','SOURCE_REPORTED') OR (p.review_state='REVIEW_PENDING' AND n.review_state='VERIFIED_FROM_PRINTED_HEADING'))");
    for(var query:queries.entrySet())report.put(query.getKey(),Long.parseLong(scalar(c,query.getValue())));
    report.put("contentAndStatusFingerprintsMatched",true);report.put("scientificReviewPerformed",false);
    System.out.println(JSON.writeValueAsString(report));
    System.out.println("All source table counts and candidate/access/projection state fingerprints matched. Live permission/REST tests remain required.");return;
   }
   throw new IllegalArgumentException("Unknown action");
  }
 }
}
