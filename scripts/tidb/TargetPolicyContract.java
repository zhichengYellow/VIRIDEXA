/** Offline target/TLS guards: does not connect to any database. */
class TargetPolicyContract {
 static String url(String schema,String options){return "jdbc:mysql://gateway.example.tidbcloud.com:4000/"+schema+"?"+options;}
 static int checks;
 static void check(String action,String schema,String options,boolean allowed){
  boolean accepted=true;
  try{TiDBReleaseTool.validateTarget(action,url(schema,options));}catch(IllegalArgumentException e){accepted=false;}
  if(accepted!=allowed)throw new AssertionError("Target policy mismatch: "+action+" / "+schema);checks++;
 }
 public static void main(String[] args){
  check("inspect-target","sys","sslMode=VERIFY_IDENTITY",true);
  for(String action:new String[]{"create-target","status","preflight","migrate","import","verify"})check(action,"sys","sslMode=VERIFY_IDENTITY",false);
  check("migrate","viridexa_tidb_release","sslMode=VERIFY_IDENTITY",true);
  check("inspect-target","mysql","sslMode=VERIFY_IDENTITY",false);
  check("import","viridexa","sslMode=VERIFY_IDENTITY",false);
  check("inspect-target","sys","sslMode=REQUIRED",false);
  check("inspect-target","sys","sslMode=VERIFY_IDENTITY&sslMode=REQUIRED",false);
  check("inspect-target","sys","sslMode=VERIFY_IDENTITY&useSSL=false",false);
  check("inspect-target","sys","sslMode=VERIFY_IDENTITY&password=example",false);
  System.out.println(checks+" offline target/TLS checks passed; cloud calls=0.");
 }
}
