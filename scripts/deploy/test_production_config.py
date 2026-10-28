#!/usr/bin/env python3
"""Structural Compose fixtures only: never start containers or contact model/Cloudflare APIs."""
import os
import secrets
import subprocess
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
class ProductionConfigTests(unittest.TestCase):
    def run_config(self,patch=None,tunnel=False):
        env=os.environ.copy()
        for key in ('MYSQL_ROOT_PASSWORD','MYSQL_APP_PASSWORD','MYSQL_MIGRATOR_PASSWORD','MYSQL_IMPORTER_PASSWORD','MYSQL_ACCOUNT_PASSWORD','MYSQL_REVIEW_PASSWORD','TURNSTILE_SECRET','CLOUDFLARE_TUNNEL_TOKEN'):
            env[key]=secrets.token_hex(32)
        env.update(WEB_ORIGIN='https://deployment-fixture.example.invalid',TURNSTILE_HOSTNAMES='deployment-fixture.example.invalid',TURNSTILE_SITE_KEY='isolated-configuration-fixture',SESSION_COOKIE_SECURE='true',AGENT_MODEL_ENABLED='false')
        env.update(patch or {})
        return subprocess.run(['bash','scripts/security/preflight.sh']+(['--tunnel'] if tunnel else []),cwd=ROOT,env=env,capture_output=True).returncode
    def test_direct_structure(self): self.assertEqual(0,self.run_config())
    def test_tunnel_structure(self): self.assertEqual(0,self.run_config(tunnel=True))
    def test_paid_model_refused(self): self.assertNotEqual(0,self.run_config({'AGENT_MODEL_ENABLED':'true','OPENROUTER_API_KEY':'configuration-fixture-not-real','OPENROUTER_MODEL':'paid/model'}))
    def test_origin_path_refused(self): self.assertNotEqual(0,self.run_config({'WEB_ORIGIN':'https://deployment-fixture.example.invalid/private'}))
if __name__=='__main__': unittest.main()
