#!/usr/bin/env python3
"""Repair only a failed, atomic V11 first step; never changes scientific rows.

Uses the configured migrator identity and the packaged Flyway runtime. Refuses
partial virtual columns, any other failed version, or a missing failed entry.
"""
import argparse, os, subprocess, tempfile, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--repair-failed-v11', action='store_true', required=True)
p.parse_args()
values = dict(line.split('=', 1) for line in (ROOT/'.env').read_text().splitlines()
              if '=' in line and not line.startswith('#'))
env = os.environ.copy()
env.update(GRAPH_REPAIR_URL='jdbc:mysql://127.0.0.1:3309/viridexa?useSSL=false&allowPublicKeyRetrieval=true',
           GRAPH_REPAIR_USER='viridexa_migrator', GRAPH_REPAIR_PASSWORD=values['MYSQL_MIGRATOR_PASSWORD'],
           GRAPH_REPAIR_LOCATION='filesystem:'+str(ROOT/'apps/api/src/main/resources/db/migration'))
source = '''
import java.sql.*;
import org.flywaydb.core.Flyway;
class GraphMigrationRepair {
 public static void main(String[] args)throws Exception {
  String url=System.getenv("GRAPH_REPAIR_URL"),user=System.getenv("GRAPH_REPAIR_USER"),password=System.getenv("GRAPH_REPAIR_PASSWORD");
  try(var c=DriverManager.getConnection(url,user,password);var s=c.createStatement()) {
   try(var r=s.executeQuery("SELECT COUNT(*),MIN(version) FROM flyway_schema_history WHERE success=0")){
    if(!r.next()||r.getInt(1)!=1||!"11".equals(r.getString(2)))throw new IllegalStateException("Expected only one failed V11");
   }
   try(var r=s.executeQuery("SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='scientific_candidate' AND COLUMN_NAME IN ('graph_namespace','graph_native_taxon_id')")){
    r.next();if(r.getInt(1)!=0)throw new IllegalStateException("Partial schema requires inspection; refusing repair");
   }
  }
  Flyway.configure().dataSource(url,user,password).locations(System.getenv("GRAPH_REPAIR_LOCATION")).load().repair();
  System.out.println("Removed failed V11 history using Flyway; application must execute the corrected online migration.");
 }
}
'''
with tempfile.TemporaryDirectory(prefix='viridexa-flyway-repair-') as temp:
    temp = Path(temp)
    jars = []
    with zipfile.ZipFile(ROOT/'apps/api/target/viridexa-api-0.1.0.jar') as bundle:
        for name in bundle.namelist():
            if name.startswith('BOOT-INF/lib/') and name.endswith('.jar'):
                target = temp/Path(name).name
                target.write_bytes(bundle.read(name)); jars.append(str(target))
    java = temp/'GraphMigrationRepair.java'; java.write_text(source)
    raise SystemExit(subprocess.call(['java', '--class-path', os.pathsep.join(jars), str(java)], env=env, cwd=ROOT))
