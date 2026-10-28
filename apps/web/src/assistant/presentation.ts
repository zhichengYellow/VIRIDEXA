import type {Evidence} from './api.ts'
export type Experience='EXPLORE'|'KNOWLEDGE'|'RESEARCH'
export const experiences:{id:Experience;name:string;description:string;examples:string[]}[]=[
 {id:'EXPLORE',name:'自由探索',description:'从一个小小的好奇开始',examples:['介绍银杏。','有哪些叶子像扇子的植物？','带我认识一下麻疯树。']},
 {id:'KNOWLEDGE',name:'知识探索',description:'沿着植物之间的联系漫游',examples:['银杏有哪些相关植物？','这些植物名称来自哪些资料？','帮我追溯当前植物的资料来源。']},
 {id:'RESEARCH',name:'深入研究',description:'从样本走向可核对的证据',examples:['帮我查看麻疯树的含油率研究记录，并比较可以直接比较的样本。','有哪些公开脂肪酸来源记录？','当前样本的计量基础是否足以比较？']},
]
export const toolLabels:Record<string,string>={searchPlants:'寻找植物线索',getPlantProfile:'阅读植物资料',getGraphNeighbors:'探索知识联系',getResearchSamples:'查找研究样本',compareOilMeasurements:'核对样本可比性',getEvidenceLineage:'追溯资料出处',model:'远程模型服务'}
export function plantIdentity(e:Evidence):{id:number;name:string;latin:string}|null{
 const match=e.category==='CATALOG'?/^plant:([1-9][0-9]*)$/.exec(e.id):null
 const id=match?Number(match[1]):e.fields.plantId
 if(typeof id!=='number'||!Number.isSafeInteger(id)||id<1||typeof e.fields.scientificName!=='string')return null
 return {id,name:typeof e.fields.nameZh==='string'&&e.fields.nameZh?e.fields.nameZh:e.fields.scientificName,latin:e.fields.scientificName}
}
export function confidenceLabel(e:Evidence){return ({HUMAN_VERIFIED:'人工确认事实',MACHINE_EXTRACTED:'机器提取的候选',SOURCE_REPORTED:'来源记录',UNRESOLVED:'尚待澄清'})[e.knowledgeConfidence||'']||'来源记录'}
export function nextQuestions(e:Evidence|undefined):string[]{const plant=e?plantIdentity(e):null;return plant?[`${plant.name}有哪些相关植物？`,`${plant.name}的资料来自哪里？`,`查看${plant.name}的研究样本。`]:['介绍银杏。','有哪些公开的植物来源？']}
