import java.sql.*;import java.nio.file.*;import java.util.zip.GZIPOutputStream;import java.nio.charset.StandardCharsets;
/** Isolated technical MySQL importer fixture. Never a production or TiDB release. */
class ImportContract {
 static void execute(Connection c,String text)throws Exception{try(var s=c.createStatement()){s.execute(text);}}
 static long count(Connection c,String table)throws Exception{try(var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+table)){r.next();return r.getLong(1);}}
 public static void main(String[] args)throws Exception{
  if(!System.getenv("TIDB_TEST_SCHEMA").startsWith("viridexa_tidb_test_"))throw new IllegalStateException("Production forbidden");
  Path bundle=Files.createTempDirectory("viridexa-import-contract-");
  try(var out=new GZIPOutputStream(Files.newOutputStream(bundle.resolve("business-data.sql.gz")))){out.write(("INSERT INTO `import_child` (`id`,`parent_id`) VALUES (1,1);\nINSERT INTO `import_child` (`id`,`parent_id`) VALUES (2,2);\n").getBytes(StandardCharsets.UTF_8));}
  try(var c=DriverManager.getConnection(System.getenv("TIDB_TEST_DB_URL"),System.getenv("TIDB_TEST_MIGRATOR"),System.getenv("TIDB_TEST_PASSWORD"))){
   execute(c,"CREATE TABLE import_parent(id INT PRIMARY KEY)");execute(c,"CREATE TABLE import_child(id INT PRIMARY KEY,parent_id INT NOT NULL,FOREIGN KEY(parent_id) REFERENCES import_parent(id))");execute(c,"INSERT INTO import_parent VALUES(1)");
   boolean rejected=false;try{TiDBReleaseTool.importData(c,bundle,"e".repeat(64));}catch(SQLException expected){rejected=true;}
   if(!rejected||count(c,"import_child")!=1||count(c,"tidb_release_import_progress")!=1)throw new IllegalStateException("Failed statement was not atomically rolled back");
   execute(c,"INSERT INTO import_parent VALUES(2)");c.commit();TiDBReleaseTool.importData(c,bundle,"e".repeat(64));
   if(count(c,"import_child")!=2||count(c,"tidb_release_import_progress")!=2)throw new IllegalStateException("Resume failed");
   TiDBReleaseTool.importData(c,bundle,"e".repeat(64));if(count(c,"import_child")!=2)throw new IllegalStateException("Duplicate import");
   rejected=false;try{TiDBReleaseTool.importData(c,bundle,"f".repeat(64));}catch(IllegalStateException expected){rejected=true;}if(!rejected)throw new IllegalStateException("Different manifest accepted");c.rollback();
   execute(c,"UPDATE tidb_release_import_progress SET statement_sha256=REPEAT('0',64) WHERE sequence_id=1");c.commit();rejected=false;try{TiDBReleaseTool.importData(c,bundle,"e".repeat(64));}catch(IllegalStateException expected){rejected=true;}if(!rejected||count(c,"import_child")!=2)throw new IllegalStateException("Checkpoint tampering accepted");c.rollback();
   System.out.println("Atomic rollback, resume, repeat import, manifest mismatch and checkpoint mismatch contracts passed on isolated MySQL.");
  }
 }
}
