import test from 'node:test'
import assert from 'node:assert/strict'
import {searchPlants} from '../../api/plants.ts'
test('plant search forwards cancellation and encodes a literal name', async () => {
  const original=globalThis.fetch, ctl=new AbortController()
  globalThis.fetch=async (url,init)=>{assert.equal(init?.signal,ctl.signal);assert.equal(new URL(String(url),'http://local').searchParams.get('query'),'A & B');return new Response(JSON.stringify({items:[],total:0}),{status:200})}
  try{assert.deepEqual(await searchPlants(' A & B ',3,ctl.signal),{items:[],total:0})}finally{globalThis.fetch=original}
})
test('failed plant search cannot return an apparent empty successful result',async()=>{
 const original=globalThis.fetch;globalThis.fetch=async()=>new Response('',{status:503})
 try{await assert.rejects(searchPlants(''),/503/)}finally{globalThis.fetch=original}
})
