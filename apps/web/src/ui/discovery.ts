export type DiscoveryCriterion = {key:string;label:string;description:string;field:'leafShape'|'flowerColour'|'growthForm';value:string}
export const discoveryCriteria:DiscoveryCriterion[] = [
 {key:'leaves',label:'认识叶片',description:'从披针形的叶子，发现不同的轮廓。',field:'leafShape',value:'LANCEOLATE'},
 {key:'flowers',label:'欣赏花朵',description:'在来源资料里，寻找白色的花。',field:'flowerColour',value:'WHITE'},
 {key:'trees',label:'探索树木',description:'顺着生长型记载，认识树木。',field:'growthForm',value:'TREE'},
]
export type DiscoveryMatch = {factId:string;knowledgeConfidence:string;plant:Record<string,unknown>}
export type DiscoveryResult = {status:string;matches:DiscoveryMatch[];truncated:boolean;ignoredUnknownCriteria:Record<string,string>}
export function discoveryName(match:DiscoveryMatch):string {return typeof match.plant.scientific_name==='string'?match.plant.scientific_name:'名称未提供'}
export function discoveryParams(query:Record<string,unknown>):URLSearchParams {
 const params=new URLSearchParams()
 for(const key of ['leafShape','flowerColour','growthForm','leafMargin','fruitType']) if(typeof query[key]==='string'&&query[key])params.set(key,query[key] as string)
 return params
}
export function uniquePlants<T extends {id:number}>(featured:T[],records:T[]):T[]{return [...new Map([...featured,...records].map(p=>[p.id,p])).values()]}
export function discoverySnippet(match:DiscoveryMatch):string {
 const traits=match.plant.traits
 if(!traits||typeof traits!=='object'||Array.isArray(traits))return ''
 const titles:Record<string,string>={'leaf shape':'叶片轮廓','flower colour':'花色','habit':'生长姿态','fruit type':'果实类型','leaf margin':'叶缘'}
 return Object.entries(titles).flatMap(([key,title])=>{const value=(traits as Record<string,unknown>)[key];return typeof value==='string'&&value.trim()?[`${title}：${value}`]:[]}).slice(0,3).join(' · ')
}
