<script setup lang="ts">
import {onMounted,onUnmounted,ref} from 'vue'
import {discoveryCriteria,type DiscoveryCriterion,type DiscoveryResult} from '../ui/discovery'
import UiIcon from './UiIcon.vue'
const host=ref<HTMLElement|null>(null),available=ref<DiscoveryCriterion[]>([]),loading=ref(false),loaded=ref(false),failed=ref(false)
const controller=new AbortController();let observer:IntersectionObserver|undefined
async function load(){if(loading.value)return;loading.value=true;failed.value=false
 const results:PromiseSettledResult<DiscoveryCriterion|null>[]=[]
 // Existing identification scans a bounded source batch; avoid three competing DB scans.
 for(const item of discoveryCriteria){if(controller.signal.aborted)return;try{const r=await fetch('/api/v1/science/identify?'+new URLSearchParams({[item.field]:item.value}),{signal:controller.signal});if(!r.ok)throw Error();const data=await r.json() as DiscoveryResult;results.push({status:'fulfilled',value:data.matches.length?item:null})}catch(reason){results.push({status:'rejected',reason})}}

 if(controller.signal.aborted)return;available.value=results.flatMap(r=>r.status==='fulfilled'&&r.value?[r.value]:[]);failed.value=results.some(r=>r.status==='rejected');loading.value=false;loaded.value=true
}
onMounted(()=>{if(!host.value)return;observer=new IntersectionObserver(entries=>{if(entries.some(e=>e.isIntersecting)){observer?.disconnect();void load()}},{rootMargin:'180px'});observer.observe(host.value)})
onUnmounted(()=>{observer?.disconnect();controller.abort()})
</script>
<template><div ref="host" class="discovery-paths" aria-label="有来源支持的外观探索"><RouterLink v-for="item in available" :key="item.key" :to="{path:'/explore',query:{[item.field]:item.value}}"><UiIcon :name="item.key==='flowers'?'spark':item.key==='trees'?'network':'leaf'" :size="32"/><h3>{{item.label}}</h3><p>{{item.description}}</p><span>顺着这个细节逛逛 <UiIcon name="arrow" :size="18"/></span></RouterLink><p v-if="loading" class="discovery-muted" role="status">正在寻找有来源支持的探索线索…</p><p v-else-if="loaded&&!available.length" class="discovery-muted">{{failed?'线索暂时没有读到。':'这一批资料还不足以形成外观探索入口。'}}<button v-if="failed" class="plain-button" @click="load">重新寻找线索</button><RouterLink to="/explore">先从一个名字开始 →</RouterLink></p><small v-if="available.length">按当前来源特征查询提供入口；不是植物鉴定或人工核验结论。</small></div></template>
