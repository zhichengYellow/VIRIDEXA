<script setup lang="ts">
import '../styles/workspaces.css'
import {useRoute,useRouter} from 'vue-router'
import CatalogReviewPanel from '../components/CatalogReviewPanel.vue'
import { useAccount } from '../stores/account'
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'

type Candidate = Record<string, unknown> & { id: number | string; source_id?: unknown; pipeline?: unknown; locator?: unknown; raw_payload?: unknown; normalized_payload?: unknown; normalization_state?: unknown; reason?: unknown; version?: number; review_state?: unknown; sha256?: unknown; license_state?: unknown; integrity_state?: unknown; dataset?: unknown; path?: unknown }
type Session = { username: string; roles: string[] }
type Page = { items: Candidate[]; page: number; size: number; total: number }
type Lineage = { candidate: Record<string, unknown>[]; documents: Record<string, unknown>[]; decisions: Record<string, unknown>[] }
type Source = { id: number | string; dataset?: unknown; path?: unknown; sha256?: unknown; license_state?: string; integrity_state?: unknown; license_version?: number }
const route=useRoute(),router=useRouter(),account=useAccount()
const queueOpen=ref(true)
const session = ref<Session | null>(null)
const filters = reactive({ sourceId: '', species: '', status: '', sampleId: '', pipeline: '' })
const page = ref<Page>({ items: [], page: 0, size: 20, total: 0 })
const currentPage = ref(0)
const pageCount = computed(() => Math.max(1, Math.ceil(page.value.total / page.value.size)))
const selected = ref<Candidate | null>(null)
const lineage = ref<Lineage | null>(null)
const sources = ref<Source[]>([])
const licenseChoices = reactive<Record<string, string>>({})
const licenseRationales = reactive<Record<string, string>>({})
const action = ref('')
const rationale = ref('')
const busy = ref(false)
const error = ref('')
const notice = ref('')
const canReview = computed(() => Boolean(session.value?.roles?.some(role => ['admin', 'role_admin', 'reviewer', 'science_reviewer'].includes(role.toLowerCase()))))
const isAdmin = computed(() => Boolean(session.value?.roles?.some(role => ['admin', 'role_admin'].includes(role.toLowerCase()))))
const decisions = ['APPROVED', 'REJECTED', 'NEEDS_EVIDENCE', 'REVOKED']
const decisionConfirmation = ref(false)
let selectionEpoch = 0
function readableError(status: number, body: string) {
  const details = body ? ` ${body}` : ''
  if (status === 401) return '登录已失效，请重新登录。'
  if (status === 403) return '当前账号没有科学审核权限。'
  if (status === 409) return '候选版本已被他人更新，请刷新后重新核对。'
  if (status === 422) return `审核未通过校验，请检查候选资料和理由。${details}`
  return `请求失败（${status}）。${details}`
}
async function request<T>(url: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(url, { ...init, credentials: 'include', headers: { Accept: 'application/json', ...init.headers } })
  if (!response.ok) throw new Error(readableError(response.status, await response.text()))
  const text = await response.text()
  return (text ? JSON.parse(text) : undefined) as T
}
async function loadSession() {
  try { session.value = await request<Session>('/api/v1/admin/session'); await loadCandidates(); if (isAdmin.value) await loadSources(); await openRequestedCandidate() }
  catch (e) { if (!(e instanceof Error && e.message.startsWith('登录已失效'))) error.value = (e as Error).message }
}
async function post<T>(url: string, body?: unknown): Promise<T> {
  const csrf = await request<{ token: string; headerName: string }>('/api/v1/csrf')
  return request<T>(url, { method: 'POST', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token }, ...(body === undefined ? {} : { body: JSON.stringify(body) }) })
}
async function signOut() {
  selectionEpoch++
  busy.value = true
  try { await account.logout(); session.value = null; selected.value = null; lineage.value = null; page.value.items = []; sources.value = []; notice.value = ''; error.value = ''; rationale.value = ''; action.value = ''; Object.keys(licenseRationales).forEach(key => delete licenseRationales[key]) }
  catch (e) { error.value = (e as Error).message } finally { busy.value = false }
}
async function loadCandidates() {
  error.value = ''
  try {
    const params = new URLSearchParams({ page: String(currentPage.value), size: String(page.value.size) })
    for (const [key, value] of Object.entries(filters)) if (value.trim()) params.set(key, value.trim())
    page.value = await request<Page>(`/api/v1/admin/science/candidates?${params}`)
  } catch (e) { error.value = (e as Error).message }
}
function applyFilters() { currentPage.value = 0; void loadCandidates() }
function movePage(delta: number) { currentPage.value = Math.min(Math.max(0, currentPage.value + delta), pageCount.value - 1); void loadCandidates() }
async function loadSources() {
  try { sources.value = await request<Source[]>('/api/v1/admin/science/sources') }
  catch (e) { error.value = (e as Error).message }
}
async function submitLicense(source: Source) {
  const id = String(source.id)
  const choice = licenseChoices[id]
  const evidence = licenseRationales[id]?.trim() ?? ''
  if (!choice || evidence.length < 10 || evidence.length > 8000) return
  busy.value = true; error.value = ''; notice.value = ''
  try {
    await post(`/api/v1/admin/science/sources/${encodeURIComponent(id)}/license`, { licenseState: choice, expectedVersion: Number(source.license_version), rationale: evidence, requestId: crypto.randomUUID() })
    notice.value = `来源 ${id} 的许可判断已由 ${session.value?.username} 具名提交。许可判断与科学审核是独立决定。`
    await loadSources(); await loadCandidates()
  } catch (e) { error.value = (e as Error).message } finally { busy.value = false }
}
async function selectCandidate(candidate: Candidate) {
  decisionConfirmation.value = false
  const requestId = ++selectionEpoch
  selected.value = candidate; void router.replace({query:{...route.query,candidateId:String(candidate.id)}}); lineage.value = null; action.value = ''; rationale.value = ''; notice.value = ''; error.value = ''
  try {
    const result = await request<Lineage>(`/api/v1/admin/science/candidates/${encodeURIComponent(String(candidate.id))}/lineage`)
    if (requestId !== selectionEpoch) return
    if(!result.candidate.length){selected.value=null;throw new Error('候选记录不存在或当前不可访问。')}
    lineage.value = result
    const latest = [...result.candidate].sort((a, b) => Number(b.version ?? 0) - Number(a.version ?? 0))[0]
    if (latest) selected.value = { ...candidate, ...latest, id: candidate.id } as Candidate
  }
  catch (e) { if (requestId === selectionEpoch) error.value = (e as Error).message }
}
async function submitDecision() {
  if (!selected.value || !action.value || !rationale.value.trim()) return
  decisionConfirmation.value = false
  busy.value = true; error.value = ''; notice.value = ''
  try {
    const result = await post<{ version: number; state: string; replayed: boolean }>(`/api/v1/admin/science/candidates/${encodeURIComponent(String(selected.value.id))}/decisions`, {
      action: action.value, expectedVersion: Number(selected.value.version), rationale: rationale.value.trim(), requestId: crypto.randomUUID(),
    })
    const successMessage = `审核决定已由 ${session.value?.username} 提交：${result.state}，版本 ${result.version}${result.replayed ? '（重复请求）' : ''}。`
    await loadCandidates()
    const refreshed = page.value.items.find(item => String(item.id) === String(selected.value?.id))
    if (selected.value) await selectCandidate(refreshed ?? {...selected.value,review_state:result.state,version:result.version})
    notice.value = successMessage
  } catch (e) { error.value = (e as Error).message } finally { busy.value = false }
}
function parsePayload(value: unknown): unknown {
  if (typeof value !== 'string') return value
  try { return JSON.parse(value) as unknown } catch { return value }
}
function payloadField(candidate: Candidate | null, key: string): unknown {
  const payload = parsePayload(candidate?.normalized_payload)
  return payload && typeof payload === 'object' ? (payload as Record<string, unknown>)[key] : undefined
}
function json(value: unknown) { const parsed = parsePayload(value); return parsed == null ? '未提供' : JSON.stringify(parsed, null, 2) }
function field(value: unknown) { return value == null || value === '' ? '未提供' : String(value) }
function canPreviewSource(source: Source) {
  if (source.license_state !== 'PERMITTED' || source.integrity_state !== 'VERIFIED' || typeof source.path !== 'string') return false
  return /\.(pdf|png|jpe?g)$/i.test(source.path.split(/[?#]/, 1)[0] ?? '')
}
async function openRequestedCandidate(){const id=route.query.candidateId;if(typeof id!=='string'||!id)return;if(!/^[0-9a-f]{64}$/.test(id)){selected.value=null;lineage.value=null;error.value='候选标识格式无效。';return}await selectCandidate(page.value.items.find(item=>String(item.id)===id)??{id})}
watch(()=>route.query.candidateId,id=>{if(session.value&&id!==String(selected.value?.id??''))void openRequestedCandidate()})
onMounted(loadSession)
onBeforeUnmount(() => { selectionEpoch++ })
</script>

<template>
  <main class="review-page">
    <div class="review-heading"><div><div class="section-kicker">SCIENCE REVIEW · 科学审核</div><h1>候选审核台</h1><p>审核结论将记录在服务端，并由当前登录账号具名负责。</p></div><div v-if="session" class="review-user">当前审核人 <strong>{{ session.username }}</strong><button class="plain-button" :disabled="busy" @click="signOut">退出</button></div></div>
    <div v-if="error" class="review-alert" role="alert">{{ error }}</div><div v-if="notice" class="review-success" role="status">{{ notice }}</div>
    <section v-if="!session" class="review-login"><h2>科研审核工作区</h2><p>只有授权管理员可以查看候选和提交审核决定。公众账户可以继续使用公开探索工具。</p><RouterLink class="auth-entry-button" :to="{path:'/login',query:{redirect:route.fullPath}}">使用账户登录 →</RouterLink><RouterLink class="review-public-back" to="/">继续探索公开植物 →</RouterLink></section>
    <section v-else-if="!canReview" class="review-login"><h2>无审核权限</h2><p>当前账号角色不包含科学审核权限，请联系管理员配置权限。</p></section>
    <section v-else class="review-layout" :class="{'queue-collapsed':!queueOpen}"><div class="review-queue-toolbar"><button :aria-expanded="queueOpen" aria-controls="review-queue" @click="queueOpen=!queueOpen">{{queueOpen?'收起候选队列':'展开候选队列'}}</button><span>筛选 → 读取证据 → 决策核对</span></div>
      <CatalogReviewPanel v-if="isAdmin"/><details v-if="isAdmin" class="source-license">
        <summary>来源许可判断 <small>ADMIN 专属 · 与科学审核相互独立 · {{ sources.length }} 个来源</small></summary>
        <p>逐条核对许可依据并手动选择状态。许可判断不代表候选已获科学批准；不会批量授权。</p>
        <article v-for="source in sources" :key="source.id" class="source-license-row">
          <div class="source-facts"><strong>来源 #{{ source.id }} · 当前许可 {{ field(source.license_state) }} · 许可版本 {{ field(source.license_version) }}</strong><span>数据集 {{ field(source.dataset) }} · 路径 {{ field(source.path) }}</span><span>完整性 {{ field(source.integrity_state) }} · SHA-256 {{ field(source.sha256) }}</span><a v-if="canPreviewSource(source)" class="controlled-preview" :href="`/api/v1/admin/science/sources/${encodeURIComponent(String(source.id))}/preview`" target="_blank" rel="noopener">受控预览 ↗</a></div>
          <label>人工许可判断<select v-model="licenseChoices[String(source.id)]"><option value="">请选择（不预设）</option><option value="PERMITTED">PERMITTED</option><option value="RESTRICTED">RESTRICTED</option><option value="UNKNOWN">UNKNOWN</option></select></label>
          <label>真实许可依据（10–8000 字）<textarea v-model="licenseRationales[String(source.id)]" minlength="10" maxlength="8000" rows="2" placeholder="记录许可文本、来源或限制条件"></textarea></label>
          <button :disabled="busy || !licenseChoices[String(source.id)] || (licenseRationales[String(source.id)]?.trim().length ?? 0) < 10" @click="submitLicense(source)">具名提交许可判断</button>
        </article>
        <p v-if="sources.length === 0" class="empty-review">没有可审核来源</p>
      </details>
      <nav id="review-queue" v-show="queueOpen" class="review-list" aria-label="审核队列与筛选"><form class="review-filters" @submit.prevent="applyFilters"><input v-model="filters.sourceId" aria-label="来源 ID" placeholder="来源 ID"/><input v-model="filters.species" aria-label="物种" placeholder="物种"/><input v-model="filters.sampleId" aria-label="样本 ID" placeholder="样本 ID"/><input v-model="filters.pipeline" aria-label="流水线" placeholder="Pipeline"/><select v-model="filters.status" aria-label="审核状态"><option value="">全部状态</option><option v-for="s in ['PENDING','HELD','APPROVED','REJECTED','NEEDS_EVIDENCE','REVOKED']" :key="s" :value="s">{{ s }}</option></select><button>筛选</button></form>
        <p class="review-count">{{ page.total }} 条候选</p><table class="candidate-table"><caption class="sr-only">待审核候选队列</caption><thead><tr><th>候选 / 管线</th><th>状态 / 版本</th></tr></thead><tbody><tr v-for="c in page.items" :key="c.id" :class="{active:selected?.id===c.id}"><td><button :aria-label="'查看候选 '+c.id" @click="selectCandidate(c)">{{String(c.id).slice(0,12)}}…</button><small>{{field(c.pipeline)}}</small></td><td><span>{{field(c.review_state)}}</span><small>v. {{field(c.version)}}</small></td></tr></tbody></table><p v-if="page.items.length===0" class="empty-review">没有候选记录</p>
        <div class="review-pagination"><button :disabled="currentPage === 0" @click="movePage(-1)">上一页</button><span>第 {{ currentPage + 1 }} / {{ pageCount }} 页</span><button :disabled="currentPage + 1 >= pageCount" @click="movePage(1)">下一页</button></div>
      </nav>
      <div class="review-detail" v-if="selected" aria-label="候选证据与决策检查器"><div class="candidate-header"><div><div class="section-kicker">候选 #{{ selected.id }}</div><h2>{{ field(selected.review_state) }}</h2></div><span>版本 {{ field(selected.version) }}</span></div>
        <div class="fact-grid"><div><b>物种</b><span>{{ field(selected.species ?? payloadField(selected, 'scientific_name') ?? payloadField(selected, 'scientificName') ?? payloadField(selected, 'species') ?? payloadField(selected, 'identity_text')) }}</span></div><div><b>样本</b><span>{{ field(selected.sample_id ?? selected.sampleId ?? payloadField(selected, 'sample_id')) }}</span></div><div><b>单位</b><span>{{ field(selected.unit ?? payloadField(selected, 'unit')) }}</span></div><div><b>来源 / 流水线</b><span>{{ field(selected.source_id) }} / {{ field(selected.pipeline) }}</span></div><div><b>来源版本</b><span>{{ field(selected.version_label) }}</span></div><div><b>许可状态</b><span>{{ field(selected.license_state) }}</span></div><div><b>完整性</b><span>{{ field(selected.integrity_state) }} · {{ field(selected.sha256) }}</span></div><div><b>定位</b><span>{{ field(selected.locator) }} · {{ field(selected.dataset) }} · {{ field(selected.path) }}</span></div><div><b>标准化 / 原因</b><span>{{ field(selected.normalization_state) }} · {{ field(selected.reason) }}</span></div></div>
        <p class="preview-note">原图默认不可预览；此审核台不会直接加载外部来源路径。</p><div class="payload-grid"><section><h3>原始值</h3><pre>{{ json(selected.raw_payload) }}</pre></section><section><h3>标准值</h3><pre>{{ json(selected.normalized_payload) }}</pre></section></div>
        <details class="history"><summary>来源与审核历史 <span v-if="lineage">· {{ lineage.candidate.length }} 个版本 · {{ lineage.documents.length }} 个来源文档 · {{ lineage.decisions.length }} 条决定</span></summary><div v-for="(group, name) in { 候选版本: lineage?.candidate, 来源文档: lineage?.documents, 历史决定: lineage?.decisions }" :key="name"><h4>{{ name }}</h4><pre>{{ json(group ?? []) }}</pre></div><p v-if="!lineage">正在读取历史…</p></details>
        <section class="decision"><p class="section-kicker">DECISION INSPECTOR</p><h3>提交科学审核决定</h3><p>决定将以 {{ session.username }} 名义写入审核历史。请基于证据作出负责判断。</p><label>审核结论<select v-model="action"><option value="">选择结论</option><option v-for="d in decisions" :key="d" :value="d">{{ d }}</option></select></label><label>审核理由<textarea v-model="rationale" required rows="3" placeholder="记录依据、限制与必要的证据说明"></textarea></label><button :disabled="busy || !lineage || !action || !rationale.trim()" @click="decisionConfirmation=true">核对并提交</button><section v-if="decisionConfirmation" class="state-notice state-notice--warning" aria-label="确认审核决定"><strong>请核对：{{action}} · 记录 {{selected.id}}</strong><p>审核人 {{session.username}}；依据：{{rationale}}。决定会写入服务端审核历史。</p><button :disabled="busy" @click="submitDecision">确认提交决定</button><button class="plain-button" @click="decisionConfirmation=false">返回修改</button></section></section>
      </div><div v-else class="review-placeholder">选择候选以查看原始资料、标准值和来源历史。</div>
    </section>
  </main>
</template>
