import {test} from 'node:test'
import assert from 'node:assert/strict'
import {backendAvailable} from '../backendAvailability.ts'
test('static SPA HTML and JSON errors do not impersonate a live backend',async()=>{
 const original=globalThis.fetch
 try {
  for (const response of [new Response('<html>SPA</html>',{headers:{'Content-Type':'text/html'}}),new Response('{}',{headers:{'Content-Type':'application/json'}}),new Response('',{status:503})]) {
   globalThis.fetch=async()=>response
   assert.equal(await backendAvailable(new AbortController().signal),false)
  }
 } finally {globalThis.fetch=original}
})
test('backend availability requires the real public metadata contract and forwards cancellation',async()=>{
 const original=globalThis.fetch, controller=new AbortController()
 try {
  globalThis.fetch=async(url,init)=>{assert.equal(url,'/api/v1/security/meta');assert.equal(init?.signal,controller.signal);return Response.json({turnstileEnabled:false,turnstileMode:'disabled',siteKey:'',registrationChallengeRequired:false,loginChallengeMode:'progressive',proxyTrustConfigured:false})}
  assert.equal(await backendAvailable(controller.signal),true)
  globalThis.fetch=async()=>{throw Error('offline')}
  assert.equal(await backendAvailable(controller.signal),false)
 } finally {globalThis.fetch=original}
})
