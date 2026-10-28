<script setup lang="ts">
import '../styles/workspaces.css'
import '../styles/assistant.css'
import StateNotice from '../components/StateNotice.vue'
import BotanicalImage from '../components/BotanicalImage.vue'
import ResearchContextBar from '../components/ResearchContextBar.vue'
import {computed,onMounted,onUnmounted,ref} from 'vue'
import {useRoute} from 'vue-router'
import {assistantApi,parseAgentContext,type AgentMeta,type RunView} from '../assistant/api'
import {safeEvidenceHref,terminal} from '../assistant/state'
import {experiences,toolLabels,plantIdentity,confidenceLabel,nextQuestions,type Experience} from '../assistant/presentation'
const meta=ref<AgentMeta|null>(null),question=ref(''),run=ref<RunView|null>(null),messages=ref<RunView[]>([]),error=ref(''),busy=ref(false),experience=ref<Experience>('EXPLORE'),technical=ref(false)
const route=useRoute(), parsedContext=computed(()=>parseAgentContext(route.query.context??(typeof route.query.plantId==='string'?JSON.stringify({plantId:Number(route.query.plantId),sampleIds:[]}):undefined)))
const context=computed(()=>parsedContext.value.context),contextError=computed(()=>parsedContext.value.error)
const selectedMode=computed(()=>experiences.find(x=>x.id===experience.value)!),remaining=computed(()=>meta.value?.freeModelBudget&&meta.value.freeModelBudget.usedToday>=0?Math.max(0,meta.value.freeModelBudget.dailyLimit-meta.value.freeModelBudget.usedToday):null)
const cards=computed(()=>{const seen=new Set<number>();return (run.value?.answer?.claims??[]).flatMap(e=>{const p=plantIdentity(e);if(!p||seen.has(p.id))return [];seen.add(p.id);return [{...p,evidence:e}]})})
const suggestions=computed(()=>nextQuestions(run.value?.answer?.claims.find(e=>plantIdentity(e)!==null)))
let stopped=false,generation=0,controller:AbortController|null=null
onMounted(async()=>{if(typeof route.query.question==='string')question.value=route.query.question.slice(0,1500);try{meta.value=await assistantApi.meta()}catch(e){error.value=(e as Error).message}})
onUnmounted(()=>{stopped=true;generation++;controller?.abort();if(run.value&&!terminal(run.value.status))void assistantApi.cancel(run.value.id).catch(()=>{})})
async function ask(text=question.value){
 const q=text.trim();if(!q||busy.value)return;if(contextError.value){error.value=contextError.value;return}
 const previous=run.value&&terminal(run.value.status)&&!['CANCELLED','FAILED','TIMED_OUT'].includes(run.value.status)?run.value:null
 if(previous)messages.value=[...messages.value,previous].slice(-5)
 const epoch=++generation;error.value='';busy.value=true;run.value=null;question.value=q;stopped=false;const request=new AbortController();controller=request
 try{
  let r=await assistantApi.start(q,request.signal,context.value??undefined,experience.value,previous?.id)
  if(epoch!==generation){if(!terminal(r.status))void assistantApi.cancel(r.id);return}run.value=r
  const start=Date.now()
  while(!stopped&&!terminal(r.status)&&Date.now()-start<90000){await new Promise(resolve=>setTimeout(resolve,500));if(stopped||epoch!==generation)break;r=await assistantApi.poll(r.id,request.signal);if(epoch===generation)run.value=r}
  if(!stopped&&!terminal(r.status)){await assistantApi.cancel(r.id);error.value='探索已超时并请求停止，请稍后再试。'}
  if(epoch===generation)meta.value=await assistantApi.meta().catch(()=>meta.value)
 }catch(e){if((e as Error).name!=='AbortError')error.value=(e as {status?:number}).status===404?'研究对象或上轮会话已不可访问。请清空会话后重新开始。':(e as Error).message}
 finally{if(epoch===generation){busy.value=false;controller=null}}
}
async function cancel(){if(!run.value)return;stopped=true;try{run.value=await assistantApi.cancel(run.value.id);controller?.abort()}catch(e){error.value=(e as Error).message}}
async function clear(){if(busy.value)return;try{await assistantApi.clear();generation++;messages.value=[];run.value=null;question.value='';error.value=''}catch(e){error.value=(e as Error).message}}
function follow(text:string){question.value=text;void ask(text)}
</script>
<template>
<main class="agent-garden">
 <header class="agent-welcome">
  <div class="agent-welcome-copy"><p class="section-kicker">VIRIDEXA · EXPLORE NATURE WITH INTELLIGENCE</p><h1>问问植序<span>让好奇，慢慢生长。</span></h1><p>一片叶子的形状，一个植物的名字。沿着真实资料，发现自然里的小小奥秘。</p><div class="agent-service-state"><span class="agent-state-dot" aria-hidden="true"></span>{{meta?.modelConfigured?'免费模型已配置 · 调用前检查价格与额度':'领域检索模式 · 无远程模型参与'}}<span v-if="remaining!==null">今日模型余量 {{remaining}} / 30</span></div><p v-if="meta?.freeModelBudget?.killSwitch" class="agent-budget-note">远程模型已由安全开关停止，公开领域检索仍然可用。</p><p v-else-if="remaining!==null&&remaining<6" class="agent-budget-note">免费额度即将用尽，之后继续通过领域检索探索。</p></div>
  <BotanicalImage name="Ginkgo biloba" :caption="true" sizes="(max-width:700px) 60vw, 350px"/>
 </header>
 <nav class="agent-experience" aria-label="探索方式"><button v-for="mode in experiences" :key="mode.id" :aria-pressed="experience===mode.id" :disabled="busy" @click="experience=mode.id"><b>{{mode.name}}</b><small>{{mode.description}}</small></button></nav>
 <ResearchContextBar v-if="context" :context="context" title="当前研究对象 · 服务端重新校验权限"/>
 <StateNotice v-if="contextError||error" tone="error" title="探索暂未完成">{{contextError||error}}</StateNotice>
 <section class="agent-conversation" aria-label="植物探索会话">
  <details v-if="messages.length" class="agent-previous"><summary>之前的探索 · {{messages.length}} 轮</summary><article v-for="item in messages" :key="item.id"><b>{{item.question}}</b><p>{{item.answer?.summary}}</p><small>历史摘要；本轮引用会重新检查公开状态。</small></article></details>
  <section v-if="run" class="agent-answer" aria-label="本轮探索结果">
   <div class="agent-question"><small>你的好奇</small><h2>{{run.question}}</h2></div>
   <div class="agent-answer-body"><div class="agent-answer-heading"><span class="agent-signature">植</span><b>植序的探索笔记</b><span aria-live="polite">{{busy?'正在探索…':run.status==='CANCELLED'?'已取消':run.status==='TIMED_OUT'?'已超时':'本轮探索结束'}}</span></div>
   <p class="agent-answer-text">{{run.answer?.summary||'正在查询允许公开的植物资料。只有真实返回的记录会成为回答依据。'}}</p>
   <p v-if="run.mode==='SEARCH_FALLBACK'" class="agent-subtle">本轮使用确定性领域检索，没有将它描述为模型自主推理。</p>
   <div v-if="cards.length" class="agent-plant-cards"><article v-for="plant in cards" :key="plant.id"><RouterLink :to="`/plants/${plant.id}`" :aria-label="`认识${plant.name}`"><BotanicalImage :name="plant.latin" :plant-id="plant.id" sizes="(max-width:700px) 85vw, 280px"/></RouterLink><div><small>{{confidenceLabel(plant.evidence)}}</small><h3><RouterLink :to="`/plants/${plant.id}`">{{plant.name}}</RouterLink></h3><i>{{plant.latin}}</i><p v-if="plant.evidence.fields.familyNameZh">{{plant.evidence.fields.familyNameZh}}</p><nav><RouterLink :to="{path:'/knowledge-graph',query:{nodeId:`plant:${plant.id}`}}">探索联系 ↗</RouterLink><RouterLink :to="{path:'/workbench',query:{plantId:plant.id}}">研究资料 ↗</RouterLink></nav></div></article></div>
   <section v-if="run.answer?.claims.length" class="agent-references"><h3>可以回去核对的线索 <small>{{run.answer.claims.length}}</small></h3><details v-for="item in run.answer.claims" :key="item.id"><summary><span class="agent-confidence">{{confidenceLabel(item)}}</span><b>{{item.fields.nameZh||item.label}}</b><span>查看出处 ＋</span></summary><p class="long-id">记录 {{item.id}}</p><dl><template v-for="(value,key) in item.fields" :key="key"><dt>{{key}}</dt><dd>{{typeof value==='string'?value:JSON.stringify(value)}}</dd></template></dl><a v-if="safeEvidenceHref(item.href)" :href="safeEvidenceHref(item.href)!" target="_blank" rel="noopener">打开真实记录 ↗</a><RouterLink v-if="item.graphNodeId" :to="{path:'/knowledge-graph',query:{nodeId:item.graphNodeId}}">沿知识网络追溯 ↗</RouterLink><pre v-if="item.sources.length">{{JSON.stringify(item.sources,null,2)}}</pre></details></section>
   <details class="agent-trail" :open="technical||undefined"><summary>查看探索过程 <span>{{run.trace.filter(t=>t.tool!=='model').length}} 条领域工具事件 · {{run.elapsedMs}} ms</span></summary><label><input v-model="technical" type="checkbox"/> 技术演示详情</label><ol><li v-for="t in run.trace" :key="t.sequence" :class="{'is-running':t.status==='RUNNING'}"><b>{{toolLabels[t.tool]||t.tool}}</b><span>{{t.status==='RUNNING'?'进行中':t.status==='SUCCESS'?'已找到公开记录':t.status==='INSUFFICIENT_EVIDENCE'?'尚未找到足够证据':t.status}}</span><p>{{t.message}}</p><small>{{t.elapsedMs}} ms · {{t.resultCount}} 条记录</small><template v-if="technical"><code>{{t.sequence}}. {{t.tool}}</code><p v-if="t.arguments&&Object.keys(t.arguments).length">参数摘要：{{JSON.stringify(t.arguments)}}</p><p class="long-id">{{t.recordIds.join(' · ')||'未引用记录'}}</p></template></li></ol><p>仅展示真实调用事件，不展示模型内部思维链。</p></details>
   <details v-if="run.answer?.limitations.length" class="agent-boundaries"><summary>资料边界与数据不足说明</summary><p v-for="limitation in run.answer.limitations" :key="limitation">{{limitation}}</p></details>
   <p class="agent-subtle">远程请求 {{run.usage.modelCalls}} 次 · 领域工具 {{run.usage.toolCalls}} 次 · {{run.usage.tokenUsageComplete?`Token ${run.usage.inputTokens+run.usage.outputTokens}`:'Token 未完整返回'}}<span v-if="run.usage.actualCost!==null&&run.usage.actualCost!==undefined"> · 供应商报告费用 ${{run.usage.actualCost}}</span><span v-else-if="run.usage.modelCalls"> · 费用未确认</span><span v-else> · 未发生模型费用</span><span v-if="run.usage.actualModels?.length"> · {{[...new Set(run.usage.actualModels)].join(' / ')}}</span></p>
   <div v-if="!busy&&run.answer" class="agent-followups"><span>沿着好奇继续</span><button v-for="q in suggestions" :key="q" @click="follow(q)">{{q}} ↗</button></div>
   </div>
  </section>
  <section v-else class="agent-invitation"><p>不必知道专业术语。</p><h2>你想先认识哪一片叶子？</h2><div><button v-for="q in selectedMode.examples" :key="q" :disabled="busy" @click="question=q">{{q}} <span>↗</span></button></div><small>资料不足时，我们会告诉你缺少什么。</small></section>
 </section>
 <form class="agent-input-dock" @submit.prevent="ask()"><label for="botanical-question">{{busy?'正在沿着资料寻找答案':'写下你的好奇'}}</label><textarea id="botanical-question" v-model="question" rows="3" maxlength="1500" :disabled="busy" placeholder="例如：介绍银杏，或看看它的资料来自哪里。" @keydown.ctrl.enter.prevent="ask()" @keydown.meta.enter.prevent="ask()"></textarea><div><small>公开证据优先 · 不会自动批准科学候选</small><button v-if="busy" type="button" class="plain-button" :disabled="!run" @click="cancel">停止探索</button><button v-else type="button" class="plain-button" @click="clear">清空会话</button><button type="submit" class="search-button" :disabled="busy||!question.trim()">{{busy?'探索中…':'开始探索 ↗'}}</button></div></form>
</main>
</template>
