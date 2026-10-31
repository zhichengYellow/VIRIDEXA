"""Isolated packaging fixtures; no external DB, model or cloud account calls."""
import os
from pathlib import Path
import subprocess
import tempfile
import unittest
ROOT=Path(__file__).resolve().parents[2]
START=ROOT/'apps/api/deploy/render-start.sh'
class RenderPackagingTests(unittest.TestCase):
    def start(self, patch=None):
        with tempfile.TemporaryDirectory() as tmp:
            java=Path(tmp)/'java';java.write_text('#!/bin/sh\nprintf "%s\\n" "$@"\n');java.chmod(0o755)
            env={'PATH':tmp+':/usr/bin:/bin','DB_URL':'jdbc:mysql://db.example.invalid:3306/viridexa?sslMode=VERIFY_IDENTITY','DB_USERNAME':'isolated_ro','DB_PASSWORD':'isolated-config-fixture','WEB_ORIGIN':'https://frontend.example.invalid'}
            env.update(patch or {})
            return subprocess.run(['sh',str(START)],env=env,capture_output=True,text=True)
    def test_low_memory_java_invocation(self):
        r=self.start();self.assertEqual(r.returncode,0);self.assertIn('-Xmx224m',r.stdout);self.assertIn('-XX:+ExitOnOutOfMemoryError',r.stdout);self.assertNotIn('DB_PASSWORD',r.stdout)
    def test_remote_model_refused(self):
        self.assertNotEqual(self.start({'AGENT_MODEL_ENABLED':'true'}).returncode,0)
        self.assertNotEqual(self.start({'OPENROUTER_API_KEY':'isolated-not-a-real-key'}).returncode,0)
    def test_verified_tls_and_no_secret_echo(self):
        for url in ['jdbc:mysql://db.example.invalid/viridexa?useSSL=false','jdbc:mysql://db.example.invalid/viridexa?sslMode=VERIFY_IDENTITY&verifyServerCertificate=false','jdbc:mysql://db.example.invalid/viridexa?sslMode=VERIFY_IDENTITY&sslMode=disabled']:
            r=self.start({'DB_URL':url});self.assertNotEqual(r.returncode,0);self.assertNotIn(url,r.stderr)
    def test_administrator_runtime_identity_refused(self):
        for user in ['root','tenant.root','avnadmin']:
            self.assertNotEqual(self.start({'DB_USERNAME':user}).returncode,0)
    def test_no_plaintext_web_origin(self):
        self.assertNotEqual(self.start({'WEB_ORIGIN':'http://frontend.example.invalid'}).returncode,0)
    def test_separate_source_build_and_no_data_copy(self):
        text=(ROOT/'apps/api/Dockerfile.render').read_text()
        self.assertIn('21-jre-jammy',text);self.assertIn('mvn -B -ntp verify',text);self.assertNotIn('COPY . ',text)
        self.assertIn('USER 10001:10001',text)
        self.assertNotIn('!target', (ROOT/'apps/api/Dockerfile.render.dockerignore').read_text())
        profile=(ROOT/'apps/api/src/main/resources/application-render.yml').read_text()
        self.assertIn('port: ${PORT:10000}',profile)
        self.assertIn('enabled: false',profile)
if __name__=='__main__':unittest.main()
