import {test} from 'node:test'
import assert from 'node:assert/strict'
import {suggestions} from '../../search/suggestions.ts'
test('blank and oversized input never requests data',async()=>{assert.deepEqual(await suggestions(' '),[]);assert.deepEqual(await suggestions('a'.repeat(81)),[])})
test('suggestion requests encode text, bound results and propagate cancellation',async()=>{const original=globalThis.fetch;const controller=new AbortController();globalThis.fetch=async(input,init)=>{assert.equal(String(input),'/api/v1/plants/suggestions?query=%E9%93%B6&limit=6');assert.equal(init?.signal,controller.signal);return new Response(JSON.stringify([{id:7,label:'银杏',matchedName:'银杏',scientificName:'Ginkgo biloba',matchType:'CHINESE_NAME_OR_ALIAS'}]))};try{assert.equal((await suggestions(' 银 ',controller.signal))[0]?.id,7)}finally{globalThis.fetch=original}})
test('errors are not disguised as no matching names',async()=>{const original=globalThis.fetch;globalThis.fetch=async()=>new Response('',{status:503});try{await assert.rejects(suggestions('银'),/暂不可用/)}finally{globalThis.fetch=original}})
