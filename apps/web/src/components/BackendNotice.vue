<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { backendAvailable } from '../security/backendAvailability'
const unavailable = ref(false), checking = ref(false)
let controller: AbortController | undefined
let timer: ReturnType<typeof setTimeout> | undefined
async function check() {
  controller?.abort()
  clearTimeout(timer)
  const current = new AbortController()
  controller = current
  checking.value = true
  timer = setTimeout(() => current.abort(), 6000)
  const available = await backendAvailable(current.signal)
  if (controller !== current) return
  clearTimeout(timer)
  unavailable.value = !available
  checking.value = false
}
onMounted(check)
onUnmounted(() => { controller?.abort(); controller = undefined; clearTimeout(timer) })
</script>
<template>
  <aside v-if="unavailable" class="backend-notice" role="status" aria-label="后端连接状态">
    <div><strong>后端尚未连接 · 前端展示模式</strong><p>目前可浏览页面与静态介绍。植物检索、实时详情与图片、图谱数据、实验台、研究助手、登录注册和审核暂不可用；技术看板历史快照不代表当前数据库。</p></div>
    <button type="button" :disabled="checking" @click="check">{{ checking ? '检查中…' : '重新检查连接' }}</button>
  </aside>
</template>
<style scoped>
.backend-notice{margin:1rem auto;padding:1rem 1.25rem;max-width:1200px;display:flex;align-items:center;gap:1rem;background:#eef1e6;border:1px solid #d4decc;border-radius:12px;color:#304a39;font-size:.85rem;line-height:1.7}.backend-notice p{margin:.3rem 0 0}.backend-notice button{flex-shrink:0;border:1px solid #6c826a;border-radius:6px;padding:.5rem .7rem;background:transparent;color:inherit;cursor:pointer}.backend-notice button:focus-visible{outline:2px solid #304a39;outline-offset:3px}@media(max-width:700px){.backend-notice{margin:.8rem;flex-direction:column;align-items:flex-start}}
</style>
