<script setup lang="ts">
import type { AgentContext } from '../assistant/api'
import { researchContextLinks } from '../assistant/researchContext'
defineProps<{ context: AgentContext; title?: string; scope?: string }>()
</script>
<template><nav class="assistant-context" aria-label="研究上下文导航"><b>{{title||'当前研究上下文'}}</b><p v-if="context.plantId">植物 ID：{{context.plantId}}</p><p v-if="context.sampleIds.length">样本：{{context.sampleIds.join(' · ')}}</p><p v-if="context.sourceId">来源：{{context.sourceId}}</p><p v-if="context.kind">测量类型：{{context.kind}}</p><p v-if="context.plantPart">植物部位：{{context.plantPart}}</p><div class="research-context-links"><RouterLink :to="{...researchContextLinks(context).workbench,query:{...researchContextLinks(context).workbench.query,...(scope==='admin'?{scope:'admin'}:{})}}">打开工作台</RouterLink><RouterLink :to="{...researchContextLinks(context).graph,query:{...researchContextLinks(context).graph.query,...(scope==='admin'?{scope:'admin'}:{})}}">打开知识图谱</RouterLink><RouterLink :to="researchContextLinks(context).assistant">在助手中继续</RouterLink></div><small>链接保留筛选上下文；服务端会验证可访问记录。</small></nav></template>
