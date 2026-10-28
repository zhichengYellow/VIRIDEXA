<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useOnboarding } from '../stores/onboarding'
import { useAccount } from '../stores/account'
import { contextPlantId } from '../onboarding/progress'
import { searchPlants } from '../api/plants'
import UiIcon from './UiIcon.vue'
import type { PlantRecord } from '../types/plant'
const account=useAccount()
const guide = useOnboarding(), route = useRoute(), router = useRouter()
const dialog = ref<HTMLDialogElement | null>(null), title = ref<HTMLElement | null>(null)
const plant = ref<PlantRecord | null>(null), loading = ref(false), failed = ref(false), navigationError = ref('')
let controller: AbortController | null = null
let focusDestination = false
const steps = computed(() => {
  const id = plant.value?.id, name = plant.value?.nameZh || plant.value?.scientificName
  return [
    { tag: '01 / DISCOVER', title: '一个名字，就是起点。', text: '不用先掌握术语。输入中文名或学名，先认识一株真实植物。', label: '去探索植物', to: { path: '/explore' }, icon: 'search', tip: '搜索只返回已开放的真实目录；没有结果也可以换一个名字。', action: '试试中文名称，或直接点开一张植物卡片。' },
    { tag: '02 / MEET', title: '认识它，也认识它的来处。', text: name ? `以「${name}」为线索，查看名称、身份状态和关联来源。` : '选择公开植物卡片，查看名称、身份状态和关联来源。', label: id ? '打开这株植物' : '选择一株植物', to: { path: id ? `/plants/${id}` : '/' }, icon: 'leaf', tip: '目录可见，不代表外观、含油率或脂肪酸已经通过科学审核。', action: '留意「来源记载」与「正式发布」的区别。' },
    { tag: '03 / CONNECT', title: '把零散线索，连成网络。', text: '在知识图谱中点击节点，展开真实的分类和来源关系；侧栏能告诉你每条关系来自哪里。', label: '打开知识图谱', to: { path: '/knowledge-graph', query: id ? { nodeId: `plant:${id}` } : {} }, icon: 'network', tip: '实线也不等于科学确认，节点与关系的状态标签才是依据。', action: '点击一个节点，再看看右侧的来源定位。' },
    { tag: '04 / TRACE', title: '数据有边界，探索有方向。', text: '登录后，在科研实验台选择植物、样本和来源。图表与表格读取同一批记录，缺失数据会如实留空。', label: '登录并进入实验台', to: { path: '/workbench', query: id ? { plantId: String(id) } : {} }, icon: 'chart', tip: '当前没有正式测量时不会填入演示数值；目录和来源仍然可以探索。', action: '从植物筛选开始，再查看样本与证据。' },
    { tag: '05 / ASK', title: '把下一份好奇，交给助手。', text: '带着当前植物继续提问，展开检索步骤和来源卡片，看看系统实际查到了什么。', label: '开始我的研究', to: { path: '/assistant', query: id ? { context: JSON.stringify({ plantId: id, sampleIds: [] }) } : {} }, icon: 'spark', tip: '未配置模型时使用真实领域检索，不会假装完成大模型推理。', action: '试着问：「这株植物有哪些资料和来源？」' },
  ]
})
const current = computed(() => steps.value[guide.progress.step]!)
async function resolvePlant() {
  controller?.abort(); const ctl = new AbortController(); controller = ctl
  loading.value = true; failed.value = false; plant.value = null
  const timer = setTimeout(() => ctl.abort(), 5000)
  try {
    const id = contextPlantId(route.path, route.query)
    if (id) {
      const response = await fetch(`/api/v1/plants/${id}`, { signal: ctl.signal, headers: { Accept: 'application/json' } })
      if (response.ok) { const record = await response.json() as PlantRecord; if (controller !== ctl) return; if (record.id === id) plant.value = record }
    }
    if (!plant.value) { const result = await searchPlants('', 1, ctl.signal); if (controller !== ctl) return; plant.value = result.items[0] ?? null }
  } catch { if (controller === ctl) failed.value = true }
  finally { clearTimeout(timer); if (controller === ctl) loading.value = false }
}
async function syncDialog() {
  await nextTick()
  if (guide.open && !dialog.value?.open) { dialog.value?.showModal(); title.value?.focus(); void resolvePlant() }
  else if (!guide.open && dialog.value?.open) { controller?.abort(); dialog.value.close(); if (focusDestination) { focusDestination = false; document.getElementById('main-content')?.focus({ preventScroll: true }) } }
}
function trapFocus(event: KeyboardEvent) {
  if (event.key !== 'Tab' || !dialog.value) return
  const controls = Array.from(dialog.value.querySelectorAll<HTMLElement>('button:not(:disabled), a[href], input:not(:disabled), select:not(:disabled), textarea:not(:disabled), [tabindex="0"]')).filter(el => el.getClientRects().length > 0)
  const first = controls[0], last = controls.at(-1), active = document.activeElement
  if (!first || !last) return
  if (event.shiftKey && (active === first || active === title.value || !dialog.value.contains(active))) { event.preventDefault(); last.focus() }
  else if (!event.shiftKey && (active === last || !dialog.value.contains(active))) { event.preventDefault(); first.focus() }
}
async function visit() {
  navigationError.value = ''
  try { await router.push(current.value.to); focusDestination = true; guide.visit() }
  catch { navigationError.value = '页面暂时无法打开，请重试或先关闭指南。' }
}
watch(() => guide.open, syncDialog)
onMounted(syncDialog)
onUnmounted(() => { controller?.abort(); dialog.value?.close() })
</script>
<template>
  <Teleport to="body">
    <dialog ref="dialog" class="research-guide" aria-labelledby="guide-title" aria-describedby="guide-description" @keydown="trapFocus" @cancel.prevent="guide.dismiss" @click="e => { if(e.target === dialog) guide.dismiss() }">
      <div class="guide-shell">
        <div class="guide-top"><span>VIRIDEXA / START HERE</span><button class="guide-close" aria-label="关闭新手指南" @click="guide.dismiss"><UiIcon name="close" :size="18"/></button></div>
        <div class="guide-body">
          <aside class="guide-map" aria-label="引导步骤"><span class="guide-map-label">A LITTLE CURIOSITY.<br/>A NEW DISCOVERY.</span><nav aria-label="选择引导步骤"><button v-for="(step,i) in steps" :key="step.tag" :aria-current="guide.progress.step===i?'step':undefined" @click="guide.select(i)"><span>{{String(i+1).padStart(2,'0')}}</span>{{['发现植物','认识对象','探索关系','追踪证据','继续提问'][i]}}<b v-if="guide.progress.step===i" aria-hidden="true"><UiIcon name="chevron" :size="15"/></b></button></nav><p>每一次探索，都能回到证据。</p></aside>
          <section class="guide-content"><div class="guide-art" aria-hidden="true"><span class="guide-art-orbit"></span><span class="guide-art-symbol"><UiIcon :name="current.icon" :size="52"/></span><span class="guide-art-dot"></span><small>FIELD NOTE / {{String(guide.progress.step+1).padStart(2,'0')}}</small></div><p class="section-kicker">{{current.tag}}</p><h2 ref="title" id="guide-title" tabindex="-1">{{current.title}}</h2><p id="guide-description">{{current.text}}</p><p class="guide-task"><span aria-hidden="true"><UiIcon name="arrow" :size="16"/></span>{{current.action}}</p><div class="guide-boundary"><strong>一个小提醒</strong><p>{{current.tip}}</p></div><p v-if="loading" class="guide-feedback" role="status">正在查找可访问的真实植物…</p><p v-else-if="failed" class="guide-feedback" role="status">暂时未能读取目录，可以先从通用入口探索。</p><p v-else-if="!plant" class="guide-feedback">当前没有公开植物，通用工具入口仍可访问。</p><p v-if="navigationError" role="alert">{{navigationError}}</p>
          <div class="guide-actions"><button class="guide-skip" @click="guide.dismiss">先自己逛逛</button><button class="guide-primary" :disabled="loading" @click="visit">{{current.label}} <span aria-hidden="true"><UiIcon name="arrow" :size="16"/></span></button></div><p class="guide-footnote">随时可以从导航栏「新手指南」重新开始。</p><RouterLink v-if="!account.profile" class="guide-account-link" to="/register?guide=1" @click="guide.dismiss">需要账户？查看登录注册指南 →</RouterLink></section>
        </div>
      </div>
    </dialog>
    <aside v-if="guide.progress.active&&!guide.open" class="guide-dock" aria-label="继续新手引导"><span class="guide-dock-icon" aria-hidden="true"><UiIcon name="book" :size="21"/></span><button @click="guide.resume"><small>你的探索地图 · {{guide.progress.step+1}} / 5</small><strong>继续引导 <span aria-hidden="true"><UiIcon name="arrow" :size="15"/></span></strong></button><button class="guide-dock-close" aria-label="结束本次引导" @click="guide.dismiss"><UiIcon name="close" :size="16"/></button></aside>
  </Teleport>
</template>
