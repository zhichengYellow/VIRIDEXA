<script setup lang="ts">
import { onMounted } from 'vue'
import { usePlantsStore } from '../stores/plants'
import type { PlantRecord } from '../types/plant'

const plants = usePlantsStore()
onMounted(() => plants.search(''))

function submitSearch() {
  void plants.search(plants.query)
}

function citation(plant: PlantRecord): string {
  const pages = plant.sourcePrintedPages || plant.sourcePages
  if (plant.sourceDocument && pages) return `${plant.sourceDocument} · 第 ${pages} 页`
  if (plant.sourceDocument) return plant.sourceDocument
  if (pages) return `原始资料第 ${pages} 页`
  return '来源定位待补充'
}
</script>

<template>
  <main>
    <section class="hero-shell">
      <div class="hero-orbit orbit-one"></div>
      <div class="hero-orbit orbit-two"></div>
      <div class="hero-inner">
        <div class="hero-copy">
          <div class="eyebrow"><span class="eyebrow-line"></span>植物知识 · 科学证据 · 可追溯</div>
          <h1>读懂植物，<br /><em>从证据开始。</em></h1>
          <p class="hero-description">
            把植物名称、形态与科研记录连回可靠来源。<br class="desktop-break" />
            让探索更直观，让每一次发现都有据可查。
          </p>
          <div class="hero-stamp">
            <span class="stamp-index">01</span>
            <span>以数据库审核状态为准</span>
            <span class="stamp-rule"></span>
            <span class="stamp-text">RESEARCH PREVIEW</span>
          </div>
        </div>

        <div class="hero-illustration" aria-hidden="true">
          <div class="illustration-ring ring-outer"></div>
          <div class="illustration-ring ring-inner"></div>
          <svg viewBox="0 0 430 430" fill="none" class="botanical-art">
            <path d="M211 370C209 288 218 205 239 93" stroke="#426956" stroke-width="2" stroke-linecap="round" />
            <path d="M213 301C173 253 134 226 83 212" stroke="#678873" stroke-width="1.6" stroke-linecap="round" />
            <path d="M222 251C262 207 303 185 352 177" stroke="#678873" stroke-width="1.6" stroke-linecap="round" />
            <path d="M228 207C200 174 184 141 178 108" stroke="#7c9984" stroke-width="1.5" stroke-linecap="round" />
            <path d="M238 148C267 118 294 103 326 97" stroke="#7c9984" stroke-width="1.5" stroke-linecap="round" />
            <path d="M211 302C164 305 132 289 113 257C150 249 183 261 211 302Z" fill="#d9e6d9" stroke="#6c8b76" stroke-width="1.5" />
            <path d="M224 256C243 215 275 194 321 193C307 229 276 251 224 256Z" fill="#e1eadf" stroke="#6c8b76" stroke-width="1.5" />
            <path d="M227 207C189 194 168 167 165 129C199 142 220 167 227 207Z" fill="#d7e5d9" stroke="#6c8b76" stroke-width="1.5" />
            <path d="M237 151C246 117 272 95 311 89C300 122 276 143 237 151Z" fill="#e6ecdd" stroke="#6c8b76" stroke-width="1.5" />
            <path d="M212 347C179 319 153 310 126 315C146 345 173 358 212 347Z" fill="#e5ecdf" stroke="#8da394" stroke-width="1.3" />
            <path d="M217 370H365" stroke="#b5c5b6" stroke-width="1.2" />
            <circle cx="239" cy="93" r="4" fill="#9db79f" />
            <circle cx="83" cy="212" r="3" fill="#b4c8b6" />
            <circle cx="352" cy="177" r="3" fill="#b4c8b6" />
            <path d="M66 376C112 366 157 367 203 376" stroke="#d7ded5" stroke-width="1" />
          </svg>
          <div class="art-label label-top">MORPHOLOGY <span>形态</span></div>
          <div class="art-label label-bottom">EVIDENCE <span>证据</span></div>
        </div>
      </div>
      <div class="hero-index">V / 01 <span></span> PLANT KNOWLEDGE</div>
    </section>

    <section id="explore" class="explore-section">
      <div class="section-heading">
        <div>
          <div class="section-kicker">EXPLORE THE COLLECTION <span>探索植物</span></div>
          <h2>从一个名字，找到它的来处。</h2>
        </div>
        <div class="collection-count" v-if="!plants.loading && !plants.error">
          <strong>{{ plants.total.toLocaleString('zh-CN') }}</strong>
          <span>条记录标记为已核验</span>
        </div>
      </div>

      <form class="search-panel" @submit.prevent="submitSearch">
        <span class="search-icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none"><circle cx="10.8" cy="10.8" r="6.8" stroke="currentColor" stroke-width="1.5"/><path d="m16 16 4.2 4.2" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
        </span>
        <input v-model="plants.query" aria-label="搜索植物名称" placeholder="试试中文名、别名或拉丁学名" />
        <span class="search-hint">名称检索</span>
        <button type="submit" :disabled="plants.loading" class="search-button">
          {{ plants.loading ? '检索中' : '开始探索' }}
          <span aria-hidden="true">↗</span>
        </button>
      </form>

      <div v-if="plants.error" class="notice notice-error" role="status">
        <span class="notice-icon">!</span>
        <div><strong>暂时无法连接科研数据</strong><p>{{ plants.error }}请确认 API 与隔离数据库已启动。</p></div>
      </div>
      <div v-else-if="plants.loading" class="notice" role="status">
        <span class="loading-mark"></span><p>正在读取已核验植物记录…</p>
      </div>
      <div v-else-if="plants.items.length === 0" class="notice" role="status">
        <span class="notice-icon">⌕</span><div><strong>没有找到匹配记录</strong><p>可以尝试中文名或学名的另一种写法。</p></div>
      </div>

      <div v-else class="record-grid">
        <article v-for="(plant, index) in plants.items" :key="plant.id" class="record-card">
          <div class="record-topline">
            <span class="record-index">RECORD / {{ String(index + 1).padStart(2, '0') }}</span>
            <span class="verified-mark"><span></span>记录标记已核验</span>
          </div>
          <div class="record-title-row">
            <div>
              <h3>{{ plant.nameZh && plant.nameZh !== plant.scientificName ? plant.nameZh : '中文名待核' }}</h3>
              <p class="latin-name">{{ plant.scientificName || '学名待补充' }}</p>
            </div>
            <div class="record-id">VX<span>{{ String(plant.id).padStart(4, '0') }}</span></div>
          </div>
          <div class="taxonomy-row">
            <span v-if="plant.familyNameZh">{{ plant.familyNameZh }}</span>
            <span v-if="plant.genusNameLatin">{{ plant.genusNameLatin }}</span>
            <span v-if="plant.nameEn">{{ plant.nameEn }}</span>
          </div>
          <p v-if="plant.morphology" class="record-description">{{ plant.morphology }}</p>
          <div class="evidence-row">
            <span class="evidence-icon" aria-hidden="true">↗</span>
            <div><span class="evidence-label">来源定位</span><span class="evidence-source">{{ citation(plant) }}</span></div>
          </div>
          <div class="record-foot">
            <span>记录状态：已核验</span>
            <span>身份状态：{{ plant.scientificNameStatus === 'VERIFIED_FROM_PRINTED_HEADING' ? '学名已据原页核验' : '学名复核中' }}</span>
          </div>
        </article>
        <div v-if="plants.total > plants.items.length" class="more-card">
          <span class="more-ornament">+</span>
          <strong>更多植物，等待你的探索。</strong>
          <span>调整检索词，继续发现已核验资料。</span>
        </div>
      </div>
      <div class="section-bottomline"><span>只展示数据库标记为公开且已核验的记录</span><span>学名与来源状态单独呈现</span></div>
    </section>

    <section class="principle-strip">
      <div class="principle-leaf">✳</div>
      <div><span class="principle-kicker">OUR PRINCIPLE</span><h2>每个结论，都有自己的证据路径。</h2></div>
      <p>植物身份、来源定位和审核状态各自记录。待审核候选不会被混作正式科研结论。</p>
    </section>
  </main>
</template>
