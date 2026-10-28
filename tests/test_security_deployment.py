from pathlib import Path
import unittest,subprocess,json,os,io,tempfile,importlib.util,contextlib
from unittest.mock import patch
ROOT=Path(__file__).resolve().parents[1]

def deployment_environment():
    env=os.environ.copy()
    for key in ['MYSQL_ROOT_PASSWORD','MYSQL_APP_PASSWORD','MYSQL_MIGRATOR_PASSWORD','MYSQL_IMPORTER_PASSWORD','MYSQL_ACCOUNT_PASSWORD','MYSQL_REVIEW_PASSWORD']:
        env[key]='ISOLATED_NOT_A_REAL_PASSWORD'
    env.update(WEB_ORIGIN='https://viridexa.example',TURNSTILE_SITE_KEY='not-a-live-site-key',TURNSTILE_SECRET='NOT_A_LIVE_TURNSTILE_SECRET',TURNSTILE_HOSTNAMES='viridexa.example',SESSION_COOKIE_SECURE='true',CLOUDFLARE_TUNNEL_TOKEN='NOT_A_LIVE_TUNNEL_TOKEN')
    return env

class SecurityDeploymentTest(unittest.TestCase):
    def resolved(self,tunnel=False):
        args=['docker','compose','-f','compose.yaml','-f','compose.production.yaml']
        if tunnel:args+=['-f','compose.tunnel.yaml']
        r=subprocess.run(args+['config','--format','json'],cwd=ROOT,env=deployment_environment(),capture_output=True,text=True,check=True)
        return json.loads(r.stdout)
    def test_resolved_production_isolates_ports_and_preserves_outbound_verification(self):
        config=self.resolved();services=config['services'];api=services['api'];web=services['web']
        for key in ['db','api','web']:self.assertFalse(services[key].get('ports'))
        self.assertTrue(config['networks']['database']['internal']);self.assertFalse(config['networks']['api_egress'].get('internal',False))
        self.assertIn('api_egress',api['networks']);self.assertNotIn('api_egress',services['db']['networks'])
        self.assertEqual(api['environment']['TRUSTED_PROXY_CIDRS'],'172.30.0.10/32');self.assertEqual(api['environment']['SESSION_COOKIE_SECURE'],'true');self.assertEqual(api['environment']['API_DOCS_ENABLED'],'false');self.assertEqual(api['environment']['TURNSTILE_ENABLED'],'true')
        self.assertEqual(web['user'],'101:101');self.assertTrue(web['read_only']);self.assertIn('ALL',web['cap_drop'])
        self.assertTrue(api['read_only']);self.assertIn('ALL',api['cap_drop']);self.assertTrue(any('/tmp:' in t and 'size=64m' in t for t in api['tmpfs']))
        self.assertIn('--temptable-max-ram=64M',services['db']['command']);self.assertIn('--max-connections=40',services['db']['command'])
        for name in ['api','web','db']:
            self.assertEqual(services[name]['logging']['options'],{'max-size':'10m','max-file':'3'})
        for service in services.values():self.assertFalse(service.get('privileged',False))
    def test_tunnel_peer_cannot_join_database_or_api_networks(self):
        config=self.resolved(tunnel=True);tunnel=config['services']['cloudflared'];self.assertEqual(set(tunnel['networks']),{'edge'});self.assertFalse(tunnel.get('ports'));self.assertEqual(tunnel['networks']['edge']['ipv4_address'],'172.31.0.2');self.assertTrue(any(v.get('source','').endswith('nginx-tunnel-trusted-proxies.conf') for v in config['services']['web']['volumes']))
    def test_preflight_succeeds_without_printing_secrets_and_rejects_http_origin(self):
        env=deployment_environment();r=subprocess.run(['bash','scripts/security/preflight.sh'],cwd=ROOT,env=env,capture_output=True,text=True);self.assertEqual(r.returncode,0,r.stderr);self.assertNotIn(env['TURNSTILE_SECRET'],r.stdout+r.stderr);self.assertNotIn(env['MYSQL_ROOT_PASSWORD'],r.stdout+r.stderr)
        env['WEB_ORIGIN']='http://viridexa.example';r=subprocess.run(['bash','scripts/security/preflight.sh'],cwd=ROOT,env=env,capture_output=True,text=True);self.assertNotEqual(r.returncode,0);self.assertIn('https',r.stderr)
    def updater(self):
        spec=importlib.util.spec_from_file_location('cloudflare_refresh',ROOT/'scripts/security/update-cloudflare-cidrs.py');module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module);return module
    def test_valid_official_ranges_generate_ip_and_scheme_trust_atomically(self):
        module=self.updater()
        with tempfile.TemporaryDirectory() as folder:
            module.OUT=Path(folder)/'trust.conf'
            with patch.object(module.urllib.request,'urlopen',side_effect=[io.BytesIO(b'173.245.48.0/20\n'),io.BytesIO(b'2400:cb00::/32\n')]),contextlib.redirect_stdout(io.StringIO()):module.main()
            result=module.OUT.read_text();self.assertIn('set_real_ip_from 173.245.48.0/20',result);self.assertIn('real_ip_header CF-Connecting-IP;',result);self.assertIn('$realip_remote_addr',result);self.assertIn('"1:https" https;',result);self.assertFalse(module.OUT.with_suffix('.conf.tmp').exists())
    def test_bad_or_overbroad_ip_update_preserves_previous_configuration(self):
        module=self.updater()
        with tempfile.TemporaryDirectory() as folder:
            module.OUT=Path(folder)/'trust.conf';module.OUT.write_text('reviewed previous config')
            with patch.object(module.urllib.request,'urlopen',return_value=io.BytesIO(b'0.0.0.0/0\n')):
                with self.assertRaises(ValueError):module.main()
            self.assertEqual(module.OUT.read_text(),'reviewed previous config')
if __name__=='__main__':unittest.main()
