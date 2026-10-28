import test from 'node:test'
import assert from 'node:assert/strict'
import { contextPlantId, freshProgress, parseProgress } from '../progress.ts'
test('guide safely recovers from missing, damaged and incompatible saved progress', () => {
  for (const value of [null, '{broken', 'null', '[]', JSON.stringify({ step:99, hidden:true, completed:false, active:true }), JSON.stringify({ step:1, hidden:'false', completed:false, active:true })]) assert.deepEqual(parseProgress(value), freshProgress())
})
test('unfinished progress resumes while completed guides never show an active dock', () => {
  assert.deepEqual(parseProgress('{"step":3,"hidden":true,"completed":false,"active":true}'), {step:3,hidden:true,completed:false,active:true})
  assert.equal(parseProgress('{"step":4,"hidden":true,"completed":true,"active":true}').active,false)
})
test('only native plant identifiers become a context hint, never status or private node values', () => {
  assert.equal(contextPlantId('/plants/2', { plantId:'3', state:'APPROVED' }),2)
  assert.equal(contextPlantId('/knowledge-graph',{nodeId:'plant:5'}),5)
  assert.equal(contextPlantId('/workbench',{plantId:'4'}),4)
  for (const query of [{plantId:['1','2']},{plantId:'-1'},{plantId:'2 OR 1=1'},{plantId:'9007199254740993'},{nodeId:'candidate:private'}]) assert.equal(contextPlantId('/workbench',query),null)
})
test('assistant context keeps a valid plant hint without trusting client review claims', () => {
  assert.equal(contextPlantId('/assistant',{context:JSON.stringify({plantId:2,state:'APPROVED',sampleIds:['private']})}),2)
  for (const value of ['{broken', 'null', '{"plantId":-2}', '{"plantId":"2"}', '{"plantId":9007199254740993}']) assert.equal(contextPlantId('/assistant',{context:value}),null)
})
