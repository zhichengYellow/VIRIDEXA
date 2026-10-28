import {test} from 'node:test'
import assert from 'node:assert/strict'
import {researchContextLinks} from '../researchContext.ts'

test('research links use destination-supported query keys and graph node namespace',()=>{
 const links=researchContextLinks({plantId:12,sampleIds:['sample:a','sample:b'],sourceId:'source:3',kind:'fatty-acid',plantPart:'seed'})
 assert.deepEqual(links.workbench,{path:'/workbench',query:{plantId:'12',sampleIds:'sample:a,sample:b',sourceId:'source:3',kind:'fatty-acid',plantPart:'seed'}})
 assert.deepEqual(links.graph,{path:'/knowledge-graph',query:{nodeId:'plant:12'}})
 assert.deepEqual(JSON.parse(String(links.assistant.query.context)),{plantId:12,sampleIds:['sample:a','sample:b'],sourceId:'source:3',kind:'fatty-acid',plantPart:'seed'})
 assert.equal('scope' in links.assistant.query,false)
})

test('links omit filters when there is no corresponding context',()=>{
 const links=researchContextLinks({sampleIds:[]})
 assert.deepEqual(links.workbench,{path:'/workbench',query:{}})
 assert.deepEqual(links.graph,{path:'/knowledge-graph',query:{}})
})
