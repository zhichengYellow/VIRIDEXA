import {test} from 'node:test'
import assert from 'node:assert/strict'
import {plantIdentity,confidenceLabel,experiences,nextQuestions} from '../presentation.ts'
import type {Evidence} from '../api.ts'
const plant:Evidence={id:'plant:33',label:'Ginkgo biloba',category:'CATALOG',verificationState:'SOURCE_REPORTED',publicationState:'METADATA_ONLY',knowledgeConfidence:'SOURCE_REPORTED',fields:{nameZh:'银杏',scientificName:'Ginkgo biloba'},sources:[],href:'/api/v1/plants/33'}
test('plant cards require server evidence identity and scientific name',()=>{assert.equal(plantIdentity(plant)?.id,33);assert.equal(plantIdentity({...plant,id:'javascript:33'}),null);assert.equal(plantIdentity({...plant,fields:{}}),null);assert.equal(plantIdentity({...plant,id:'plant:9007199254740993'}),null)})
test('source-linked cards use explicit plantId rather than guessing species',()=>{assert.equal(plantIdentity({...plant,id:'source:a',category:'SOURCE_RECORD',fields:{scientificName:'Ginkgo biloba'}}),null);assert.equal(plantIdentity({...plant,id:'source:a',category:'SOURCE_RECORD',fields:{scientificName:'Ginkgo biloba',plantId:33}})?.id,33)})
test('confidence labels distinguish source, extracted and named review',()=>{assert.equal(confidenceLabel(plant),'来源记录');assert.equal(confidenceLabel({...plant,knowledgeConfidence:'MACHINE_EXTRACTED'}),'机器提取的候选');assert.equal(confidenceLabel({...plant,knowledgeConfidence:'HUMAN_VERIFIED'}),'人工确认事实')})
test('three experiences share the same bounded agent API and grounded followups',()=>{assert.deepEqual(experiences.map(x=>x.id),['EXPLORE','KNOWLEDGE','RESEARCH']);assert.match(nextQuestions(plant)[0]!,/银杏/);assert.equal(nextQuestions(undefined).length,2)})
