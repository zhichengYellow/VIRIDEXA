import test from 'node:test'
import assert from 'node:assert/strict'
import { requireLoginOnUnauthorized } from '../access.ts'
test('expired research sessions redirect only on server 401 and retain research context',()=>{
 const calls:string[]=[]
 const location={pathname:'/workbench',search:'?plantId=2&sampleIds=sample:abc',hash:'#evidence',replace:(url:string)=>calls.push(url)}
 Object.defineProperty(globalThis,'window',{configurable:true,value:{location}})
 try {
  requireLoginOnUnauthorized(403);assert.equal(calls.length,0)
  requireLoginOnUnauthorized(401);assert.equal(new URL(calls[0]!, 'http://localhost').searchParams.get('redirect'),'/workbench?plantId=2&sampleIds=sample:abc#evidence')
  location.pathname='/plants/2';requireLoginOnUnauthorized(401);assert.equal(calls.length,1)
 } finally {Reflect.deleteProperty(globalThis,'window')}
})
