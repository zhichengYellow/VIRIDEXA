import test from 'node:test'
import assert from 'node:assert/strict'
import {discoveryParams,discoveryName,discoverySnippet,uniquePlants} from '../discovery.ts'

test('discovery URL accepts trait filters but never permission or review claims',()=>{
 const q=discoveryParams({leafShape:'LANCEOLATE',flowerColour:'WHITE',scope:'admin',publicationState:'PUBLISHED',q:'test',growthForm:['TREE']})
 assert.equal(q.toString(),'leafShape=LANCEOLATE&flowerColour=WHITE')
})
test('source discovery keeps missing names explicit without manufacturing a catalogue identity',()=>{
 assert.equal(discoveryName({factId:'source-1',knowledgeConfidence:'MACHINE_EXTRACTED',plant:{}}),'名称未提供')
 assert.equal(discoveryName({factId:'source-2',knowledgeConfidence:'SOURCE_REPORTED',plant:{scientific_name:'Ginkgo biloba'}}),'Ginkgo biloba')
})
test('curated catalogue entries and paginated results deduplicate by actual ID, not a shared name',()=>{
 const list=uniquePlants([{id:2,name:'Ginkgo'},{id:4,name:'Same'}],[{id:2,name:'Updated'},{id:5,name:'Same'}])
 assert.deepEqual(list,[{id:2,name:'Updated'},{id:4,name:'Same'},{id:5,name:'Same'}])
})

test('readable trait captions preserve source values and omit absent or structured values',()=>{
 assert.equal(discoverySnippet({factId:'1',knowledgeConfidence:'MACHINE_EXTRACTED',plant:{traits:{'leaf shape':'lanceolate','flower colour':null,habit:'herb','fruit type':{unknown:true}}}}),'叶片轮廓：lanceolate · 生长姿态：herb')
 assert.equal(discoverySnippet({factId:'2',knowledgeConfidence:'SOURCE_REPORTED',plant:{}}),'')
})
