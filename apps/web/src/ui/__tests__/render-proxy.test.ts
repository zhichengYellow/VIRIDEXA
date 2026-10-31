import {test} from 'node:test'
import assert from 'node:assert/strict'
import proxy from '../../../worker/render-proxy.mjs'
const env={API_ORIGIN:'https://viridexa-fixture.onrender.com',ASSETS:{fetch:async()=>new Response('static')}}
test('Render bridge refuses arbitrary origins and preserves static asset routing',async()=>{
 for(const origin of ['', 'http://localhost:8080','https://evil.example','https://viridexa-fixture.onrender.com/path']) assert.equal((await proxy.fetch(new Request('https://front.invalid/api/v1/plants'),{...env,API_ORIGIN:origin})).status,503)
 assert.equal(await (await proxy.fetch(new Request('https://front.invalid/technology'),env)).text(),'static')
})
test('bridge preserves CSRF and session while stripping spoofed forwarding headers and caching',async()=>{
 const original=globalThis.fetch
 try {globalThis.fetch=async(url,init)=>{assert.equal(String(url),env.API_ORIGIN+'/api/v1/account/login');const h=new Headers(init?.headers);assert.equal(h.get('Cookie'),'JSESSIONID=isolated-session');assert.equal(h.get('X-CSRF-TOKEN'),'isolated-token');assert.equal(h.get('X-Forwarded-For'),null);assert.equal(h.get('CF-Connecting-IP'),null);assert.equal(h.get('Origin'),'https://front.invalid');assert.equal(init?.redirect,'manual');return Response.json({ok:true},{headers:{'Cache-Control':'public,max-age=600','Set-Cookie':'JSESSIONID=isolated-new; Secure; HttpOnly; SameSite=Strict'}})}
 const r=await proxy.fetch(new Request('https://front.invalid/api/v1/account/login',{method:'POST',headers:{Cookie:'JSESSIONID=isolated-session','X-CSRF-TOKEN':'isolated-token','X-Forwarded-For':'1.2.3.4','CF-Connecting-IP':'1.2.3.4',Origin:'https://front.invalid'},body:'{}'}),env)
 assert.equal(r.status,200);assert.equal(r.headers.get('Cache-Control'),'no-store');assert.match(r.headers.get('Set-Cookie')!,/Secure/)
 }finally{globalThis.fetch=original}
})
test('bridge bounds bodies and refuses destructive methods',async()=>{
 assert.equal((await proxy.fetch(new Request('https://front.invalid/api/v1/account/login',{method:'POST',body:'a'.repeat(65537)}),env)).status,413)
 assert.equal((await proxy.fetch(new Request('https://front.invalid/api/v1/plants',{method:'DELETE'}),env)).status,405)
})
test('upstream errors and redirects never fabricate query results',async()=>{
 const original=globalThis.fetch
 try {globalThis.fetch=async()=>{throw Error('isolated network failure')};assert.equal((await proxy.fetch(new Request('https://front.invalid/api/v1/plants'),env)).status,504)
 globalThis.fetch=async()=>new Response(null,{status:302,headers:{Location:'https://evil.example'}});assert.equal((await proxy.fetch(new Request('https://front.invalid/api/v1/plants'),env)).status,502)
 }finally{globalThis.fetch=original}
})
