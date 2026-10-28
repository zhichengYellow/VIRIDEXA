<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RouterLink } from 'vue-router'
import StatusIndicator from '../components/StatusIndicator.vue'
import UiIcon from '../components/UiIcon.vue'
import { graphApi, type GraphMeta } from '../graph/api'
import { assistantApi, type AgentMeta } from '../assistant/api'
import { workbenchApi, type Overview } from '../workbench/api'
import { securityMeta, type SecurityMeta } from '../security/api'
type Showcase={counts:{plants:number;publicPlants:number;candidates:number;publicSourceRecords:number;humanVerified:number;samples:number;unlinkedSamples:number;held:number;sourceVersions:number;unknownLicenses:number};pipelines:{pipeline:string;total:number;normalized:number;held:number;publicRecords:number}[];sources:{id:number|string;dataset:string;version:string;sha256:string;integrityState:string;licenseState:string;records:number;publicRecords:number}[]}
type Engineering={completedAt:string;uniqueAutomatedTests:number;passed:number;failed:number;skipped:number;suites:{name:string;passed:number}[];scope:string}
type BotanicalRelease={date:string;scope:string;chineseNames:number;chineseCoveragePercent:number;sp2000Version:string;sp2000Matches:number;media:number;mediaPlants:number;withMainImage:number;protectedHashesMatched:number;scientificFacts:number}
const botanicalRelease=ref<BotanicalRelease|null>(null)
const engineering=ref<Engineering|null>(null)
const meta=ref<GraphMeta|null>(null),workbench=ref<Overview|null>(null),agent=ref<AgentMeta|null>(null),security=ref<SecurityMeta|null>(null),showcase=ref<Showcase|null>(null),showcaseStatus=ref('正在获取展示统计'),graphError=ref('')
const nodes=computed(()=>meta.value?Object.values(meta.value.nodeCounts).reduce((a,b)=>a+b,0):null),edges=computed(()=>meta.value?Object.values(meta.value.edgeCounts).reduce((a,b)=>a+b,0):null)
const n=(v:number|undefined)=>v===undefined?'—':v.toLocaleString('zh-CN'), labels:Record<string,string>={TAXONOMY:'植物分类',MORPHOLOGY:'形态资料',LIPID:'脂质与成分'}
function sourceInfo(dataset:string){
 if(dataset.includes('wfo_'))return {name:'World Flora Online',purpose:'目标名称、接受名及异名关系切片；未全量导入全球名录'}
 if(dataset.includes('combined.zip'))return {name:'NHM · Combined Traits',purpose:'机器提取的性状词与长度数值；不作为人工鉴定'}
 if(dataset.includes('flora_of_china'))return {name:'Flora of China · NHM',purpose:'来源性状投影；与 Combined 数据可能重叠，不相加为独立物种'}
 if((dataset.includes('gbif')||dataset==='match_candidates_20261011.jsonl'))return {name:'GBIF',purpose:'保留名称匹配响应及分类候选；不将匹配等同身份确认'}
 if(dataset.includes('nhm_taxon_'))return {name:'NHM · 分类映射资料',purpose:'名称核对与导入映射记录；未通过的身份仍保留待解析状态'}
 if(dataset.includes('FoodData'))return {name:'USDA FoodData Central',purpose:'每 100 g 食品营养量；与种子油组成严格分开'}
 if(dataset.includes('课程'))return {name:'课程 PDF',purpose:'课程植物、含油率记录的原始定位；全文不对外复制'}
 if(dataset.includes('.pdf'))return {name:'科研论文 · '+dataset,purpose:'登记原始文献哈希和表页定位；未把 PDF 直接当已解析记录'}
 if(dataset.includes('oil_samples'))return {name:'课程含油率',purpose:'独立样本、原始百分比与计量基础'}
 if(dataset.includes('plants.json'))return {name:'课程植物目录',purpose:'原始标题中的学名、中文名和页码；目录公开与科学审核分开'}
 if(dataset.includes('fatty_acid'))return {name:'脂肪酸研究提取',purpose:'FAME / 油脂组成独立保留；原文与完整表格不公开复制'}
 return {name:dataset,purpose:'保留来源版本、处理范围与异常状态'}
}
onMounted(()=>{
 void fetch('/botanical-release.json').then(async r=>{if(!r.ok)throw Error();return await r.json() as BotanicalRelease}).then(v=>botanicalRelease.value=v).catch(()=>{})
 void fetch('/engineering-evidence.json').then(async r=>{if(!r.ok)throw Error();return await r.json() as Engineering}).then(v=>engineering.value=v).catch(()=>{})
 void fetch('/api/v1/showcase').then(async r=>{if(!r.ok)throw Error();return await r.json() as Showcase}).then(data=>{showcase.value=data;showcaseStatus.value='当前数据库 · GET /api/v1/showcase'}).catch(()=>{showcaseStatus.value='展示统计尚未获取；可重试或查看来源页面'})
 void graphApi.meta().then(data=>{meta.value=data}).catch(()=>{graphError.value='图谱统计暂时不可用；节点探索入口仍可单独查询'})
 void workbenchApi.overview().then(data=>{workbench.value=data}).catch(()=>{})
 void assistantApi.meta().then(data=>{agent.value=data}).catch(()=>{})
 void securityMeta().then(data=>{security.value=data}).catch(()=>{})
})
const sourceFamilies=computed(()=>[...new Set((showcase.value?.sources??[]).map(s=>sourceInfo(s.dataset).name.split(' · ')[0]))])
const modules=[{to:'/explore',icon:'leaf',eyebrow:'01 / CATALOG',title:'植物探索',description:'检索公开目录、详情与来源定位。'},{to:'/knowledge-graph',icon:'network',eyebrow:'02 / CYTOSCAPE.JS',title:'知识网络',description:'搜索并展开植物、来源和证据关系。'},{to:'/workbench',icon:'chart',eyebrow:'03 / ECHARTS',title:'科研实验台',description:'查看公开样本与可比性边界；操作需登录。'},{to:'/assistant',icon:'spark',eyebrow:'04 / ASSISTANT',title:'研究助手',description:'免费模型白名单、受控只读工具与证据引用。'},{to:'/review',icon:'shield',eyebrow:'05 / GOVERNANCE',title:'审核工作区',description:'管理员登录后审核候选和来源许可。'}]
</script>
<template>
  <main class="technology-page">
    <section class="technology-hero">
      <div class="technology-hero-copy">
        <p class="technology-eyebrow"><span class="technology-mark"><UiIcon name="leaf" :size="17" /></span> VIRIDEXA <span>·</span> 植物科学数据平台</p>
        <p class="technology-stack-eyebrow">BEHIND THE LEAVES / 植序幕后</p><h1>每一条知识，<br><em>都有根。</em></h1>
        <p class="technology-lead">植物资料来自哪里？我们保留资料的出处，将名称与观察整理成可以探索的线索。你可以沿着一条知识，回到它的来源。</p>
        <div class="technology-hero-actions">
          <RouterLink class="technology-primary-link" to="/explore"><UiIcon name="leaf" :size="16" />浏览植物目录<UiIcon name="arrow" :size="15" /></RouterLink>
          <RouterLink class="technology-secondary-link" to="/knowledge-graph"><UiIcon name="network" :size="16" />探索知识网络</RouterLink>
        </div>
        <p class="technology-hero-note"><StatusIndicator state="SOURCE_REPORTED" />公开目录不等于人工确认的科学事实</p>
      </div>
      <div class="technology-hero-visual" aria-label="从登记来源到审阅和发布的流程示意">
        <div class="technology-orbit technology-orbit-one"></div><div class="technology-orbit technology-orbit-two"></div>
        <span class="technology-visual-leaf"><UiIcon name="leaf" :size="48" /></span>
        <div class="technology-node technology-node-source"><UiIcon name="book" :size="17" /><span>原始来源<small>版本 · 摘要</small></span></div>
        <div class="technology-node technology-node-evidence"><UiIcon name="network" :size="17" /><span>证据关系<small>出处 · 定位</small></span></div>
        <div class="technology-node technology-node-review"><UiIcon name="check" :size="17" /><span>独立审核<small>授权 · 发布</small></span></div>
        <span class="technology-visual-caption">BOTANICAL KNOWLEDGE · WITH PROVENANCE</span>
      </div>
    </section>

    <section class="technology-live-strip" aria-label="当前服务运行统计">
      <div class="technology-live-heading"><span class="technology-live-dot"></span><span>实时服务数据</span><small>API · READ ONLY</small></div>
      <div class="technology-live-stat"><small>公开植物目录</small><strong>{{ n(showcase?.counts.publicPlants) }}</strong><span>目录名录，不等于核验物种</span></div>
      <div class="technology-live-stat"><small>公开来源记录</small><strong>{{ n(showcase?.counts.publicSourceRecords) }}</strong><span>依来源授权范围展示</span></div>
      <div class="technology-live-stat"><small>人工核验事实</small><strong>{{ n(showcase?.counts.humanVerified) }}</strong><span>由审核流程明确批准</span></div>
      <div class="technology-live-stat"><small>可见工作台样本</small><strong>{{ n(workbench?.visibleSamples) }}</strong><span>样本不代表已发布事实</span></div>
      <p class="technology-live-status">{{ showcaseStatus }}<span v-if="showcase?.counts"> · {{ n(showcase.counts.held) }} 条暂缓 · {{ n(showcase.counts.unknownLicenses) }} 项许可未知</span></p>
    </section>

    <details class="museum-professional"><summary>走进技术幕后 · 展开系统架构与完整技术栈</summary><header class="vx-page-header"><div><p class="vx-kicker">01 / SYSTEM ARCHITECTURE</p><h2>技术服务于可复核的数据流程</h2><p>前端负责探索与呈现；后端执行身份、权限和发布规则；数据库保留来源和审核轨迹。</p></div></header>
    <div class="technology-architecture-flow" aria-label="请求链路"><span>Vue 3</span><i>→</i><span>Nginx</span><i>→</i><span>Spring Boot</span><i>→</i><span>MyBatis</span><i>→</i><span>MySQL</span></div><section class="technology-architecture" aria-label="系统技术架构">
      <article class="technology-architecture-card technology-architecture-ui"><div class="technology-layer-title"><span>01</span><small>交互与可视化</small></div><div class="technology-stack-main"><UiIcon name="leaf" :size="24"/><div><strong>Vue 3 + TypeScript</strong><small>组件式页面与类型约束</small></div></div><div class="technology-stack-list"><span>Vite 构建</span><span>ECharts 图表</span><span>Cytoscape.js 知识网络</span><span>Pinia · Vue Router</span></div></article>
      <div class="technology-architecture-connector"><span>HTTPS · REST</span><i>→</i></div>
      <article class="technology-architecture-card technology-architecture-api"><div class="technology-layer-title"><span>02</span><small>服务与安全边界</small></div><div class="technology-stack-main"><UiIcon name="shield" :size="24"/><div><strong>Java 21 + Spring Boot</strong><small>REST API · 领域服务 · Spring Security</small></div></div><div class="technology-stack-list"><span>会话与 CSRF</span><span>角色访问控制</span><span>MyBatis · Flyway</span><span>Nginx 反向代理</span></div></article>
      <div class="technology-architecture-connector"><span>受控持久化</span><i>→</i></div>
      <article class="technology-architecture-card technology-architecture-data"><div class="technology-layer-title"><span>03</span><small>来源与记录</small></div><div class="technology-stack-main"><UiIcon name="database" :size="24"/><div><strong>MySQL</strong><small>Docker Compose 部署环境</small></div></div><div class="technology-stack-list"><span>来源与版本</span><span>候选与证据</span><span>审核事件</span><span>公开视图</span></div></article>
      <p class="technology-version-note">版本依据：<code>apps/api/pom.xml</code> 与 <code>apps/web/package.json</code>。展示仓库锁定的运行依赖版本，不代表上游最新版本。</p>
    </section>

    </details><div class="technology-section-heading"><div><p class="technology-section-kicker">02 / DATA PIPELINES</p><h2>三条独立流水线，共用追溯规则</h2></div><p>当前统计来自展示 API；等待响应时保持空值，不填充演示数字。</p></div>
    <div class="technology-data-flow"><span v-for="(step,index) in ['采集','解析','标准化','来源血缘','领域查询','交互产品']" :key="step"><small>0{{index+1}}</small>{{step}}<UiIcon v-if="index<5" name="arrow" :size="13"/></span></div>
    <section v-if="showcase?.pipelines.length" class="technology-pipelines">
      <article v-for="(p,i) in showcase.pipelines" :key="p.pipeline" class="technology-pipeline-card"><div class="technology-pipeline-head"><span>PIPELINE 0{{ i+1 }}</span><StatusIndicator :state="p.held?'HELD':'SOURCE_REPORTED'" /></div><h3>{{ labels[p.pipeline]??p.pipeline }}</h3><p class="technology-pipeline-subtitle">{{ p.pipeline }} pipeline</p><div class="technology-pipeline-flow"><div><small>已入库</small><strong>{{ n(p.total) }}</strong></div><i aria-hidden="true"></i><div><small>已标准化</small><strong>{{ n(p.normalized) }}</strong></div><i aria-hidden="true"></i><div><small>公开记录</small><strong>{{ n(p.publicRecords) }}</strong></div></div><div class="technology-progress-track"><span :style="{width:p.total?`${Math.min(100,p.normalized/p.total*100)}%`:'0%'}"></span></div><p class="technology-pipeline-foot">{{ n(p.held) }} 条暂缓处理，不进入公开结果</p></article>
    </section>
    <p v-else class="technology-empty-state"><UiIcon name="clock" :size="18" />{{ showcaseStatus }}；pipeline 统计只在 API 返回后呈现。</p>

    <template v-if="botanicalRelease"><div class="technology-section-heading"><div><p class="technology-section-kicker">BOTANICAL RELEASE / {{botanicalRelease.date}}</p><h2>让名字与影像，回到同一株植物</h2></div><p>{{botanicalRelease.scope}}。名称公开、图像许可与科学审核分别判断。</p></div><section class="technology-pipelines" aria-label="中文名与媒体发布验收快照"><article class="technology-pipeline-card"><h3>可追溯的中文名</h3><p>{{n(botanicalRelease.chineseNames)}} 条目录记录 · {{botanicalRelease.chineseCoveragePercent}}%</p><p>SP2000 {{botanicalRelease.sp2000Version}} 接受种精确匹配 {{n(botanicalRelease.sp2000Matches)}} 条；iFlora补充元数据。原名称与来源历史保留，未匹配不猜测。</p></article><article class="technology-pipeline-card"><h3>有许可的真实影像</h3><p>{{n(botanicalRelease.media)}} 张本地持久照片 · {{n(botanicalRelease.mediaPlants)}} 条目录记录</p><p>严格检查物种、CC0 / CC BY与原图哈希。含授权静态回退，共 {{n(botanicalRelease.withMainImage)}} 条目录记录具备主图；缺图保留无图页。</p></article><article class="technology-pipeline-card"><h3>来源公开，核验独立</h3><p>{{n(botanicalRelease.protectedHashesMatched)}} / 292 原始文件哈希匹配</p><p>正式科学事实 {{botanicalRelease.scientificFacts}}；未代替用户作出科学审核。下载整包、论文和本地数据库不会随源码分发。</p></article></section></template>
    <div id="sources" class="technology-section-heading"><div><p class="technology-section-kicker">03 / SOURCES & PROVENANCE</p><h2>来源登记、许可和内容完整性</h2></div><p>来源出现于清单，只说明已登记；不自动代表许可通过、内容已解析或科学结论已审核。</p></div>
    <div v-if="sourceFamilies.length" class="technology-source-families"><span v-for="family in sourceFamilies" :key="family">{{family}}</span></div><details v-if="showcase?.sources.length" class="technology-source-register"><summary><span><b>{{showcase.sources.length}} 个真实来源版本</b><small>展开查看原始文件、版本、SHA-256、许可与处理数量</small></span><UiIcon name="chevron"/></summary><section class="technology-sources">
      <article v-for="s in showcase.sources" :key="s.id" class="technology-source-card"><div class="technology-source-top"><span class="technology-source-icon"><UiIcon name="book" :size="18" /></span><StatusIndicator :state="s.licenseState==='UNKNOWN'?'HELD':'SOURCE_REPORTED'" /></div><h3>{{ sourceInfo(s.dataset).name }}</h3><p>{{ sourceInfo(s.dataset).purpose }}</p><div class="technology-source-id">{{ s.dataset }} <span>· {{ s.version }}</span></div><div class="technology-source-meta"><span>文件摘要 <b>{{ s.integrityState==='VERIFIED'?'匹配':s.integrityState }}</b></span><span>许可状态 <b>{{ s.licenseState==='UNKNOWN'?'未知':s.licenseState }}</b></span><span>记录 / 可公开 <b>{{ n(s.records) }} / {{ n(s.publicRecords) }}</b></span></div><code>SHA-256 · {{ s.sha256||'未提供' }}</code><RouterLink :to="{path:'/knowledge-graph',query:{nodeId:'source:'+s.id}}">查看来源节点与记录<UiIcon name="arrow" :size="14" /></RouterLink></article>
    </section></details>
    <p v-else class="technology-empty-state"><UiIcon name="book" :size="18" />来源清单暂不可用。范围计划包括 WFO、NHM、GBIF、Flora of China、USDA、课程资料和科研论文；名称列示不代表来源已下载、许可已核验或内容已入库。</p>

    <section class="technology-pipelines" aria-label="Agent 旗舰工程能力"><article class="technology-pipeline-card"><p class="technology-section-kicker">EXPLORATION TRAIL</p><h3>从好奇，走到证据</h3><p>三种探索体验共用六个只读领域工具。图文卡片来自真实实体，探索轨迹只展示实际执行事件。</p><RouterLink :to="{path:'/assistant',query:{question:'介绍银杏。'}}">试试认识银杏 ↗</RouterLink></article><article class="technology-pipeline-card"><p class="technology-section-kicker">FREE-ONLY OPENROUTER</p><h3>免费策略失效，就停止</h3><p>每次远程请求前实时校验价格；零价格上限、禁付费回退、持久化原子配额，每站每日30次 / 每分钟5次。异常费用触发 Kill Switch。</p><p>{{agent?.modelConfigured?'运行配置：远程模型已配置；真实联调结果请查验收报告。':'运行配置：领域检索模式，未启用远程模型。'}}</p></article><article class="technology-pipeline-card"><p class="technology-section-kicker">GROUNDED BY DESIGN</p><h3>把回答还给真实记录</h3><p>引用 ID 必须存在于本轮公开工具结果；多轮只保留有界上下文，重新检查权限。样本比较复用实验台规则，不猜测物种或缺失单位。</p><RouterLink :to="{path:'/assistant',query:{question:'帮我查看麻疯树的含油率研究记录，并比较可以直接比较的样本。'}}">查看科研问题的证据边界 ↗</RouterLink></article></section>
    <div class="technology-section-heading"><div><p class="technology-section-kicker">04 / EXPLORE THE PLATFORM</p><h2>直接进入实际功能模块</h2></div><p>目录、网络、工作台、助手和审核均连接各自的真实路由与服务。</p></div>
    <section class="technology-module-grid"><RouterLink v-for="m in modules" :key="m.to" class="technology-module-card" :to="m.to"><span class="technology-module-eyebrow">{{ m.eyebrow }}</span><span class="technology-module-icon"><UiIcon :name="m.icon" :size="21" /></span><h3>{{ m.title }}</h3><p>{{ m.description }}</p><span class="technology-module-link">打开模块 <UiIcon name="arrow" :size="14" /></span></RouterLink></section>

    <div class="technology-section-heading"><div><p class="technology-section-kicker">05 / ACCESS & SCIENTIFIC QUALITY</p><h2>权限边界与科研数据边界</h2></div><p>公开可浏览、可供研究与人工确认是不同状态，页面不会把它们合并表达。</p></div>
    <section class="technology-boundaries">
      <article class="technology-boundary-card"><span class="technology-boundary-icon"><UiIcon name="shield" :size="20" /></span><h3>服务端强制执行访问控制</h3><ul><li>Spring Security 控制角色和会话；写操作受 CSRF 校验保护。</li><li>研究工作台和审核功能要求登录；管理员操作由后端授权。</li><li>Nginx 按认证、检索、Agent 与管理操作分别限流，超限返回 429；不构成 DDoS 防护保证。</li><li>版本化静态资源可缓存；会话、候选和 API 响应不进入共享缓存。统计聚合使用版本校验的 5 秒本地缓存。</li><li>JVM、连接池与 Agent 工具耗时只向授权管理员开放，公网网关屏蔽内部 Actuator 路径。</li></ul><RouterLink to="/review">查看权限与审核入口 <UiIcon name="arrow" :size="14" /></RouterLink></article>
      <article class="technology-boundary-card"><span class="technology-boundary-icon"><UiIcon name="book" :size="20" /></span><h3>来源证据和科学结论分开</h3><ul><li>保留来源版本、SHA-256、许可状态和证据定位；摘要匹配不等于科学审核。</li><li>机器提取结果标为候选；名称匹配不自动确认植物身份。</li><li>图表展示单位、计量基础和缺失状态，不补零或合并不可比样本。</li></ul><RouterLink to="/knowledge-graph">沿关系查看来源证据 <UiIcon name="arrow" :size="14" /></RouterLink></article>
    </section>

    <section class="technology-runtime">
      <div class="technology-runtime-intro"><p class="technology-section-kicker">LIVE / SERVICE METADATA</p><h2>当前运行状态</h2><p>状态来自当前服务接口；暂不可用的字段显示为未获取。</p></div>
      <dl class="technology-runtime-grid"><div><dt>图谱公开节点</dt><dd>{{ nodes===null?'—':n(nodes) }}</dd></div><div><dt>图谱公开关系</dt><dd>{{ edges===null?'—':n(edges) }}</dd></div><div><dt>图谱公开事实</dt><dd>{{ n(meta?.publishedFactCount) }}</dd></div><div><dt>工作台未关联样本</dt><dd>{{ n(workbench?.unlinkedSamples) }}</dd></div><div><dt>人工核验事实</dt><dd>{{ n(showcase?.counts.humanVerified) }}</dd></div><div><dt>真实模型</dt><dd>{{ agent?agent.modelConfigured?`已配置 · ${agent.model??'模型名未提供'}`:'未配置':'未获取' }}</dd></div><div><dt>Turnstile</dt><dd>{{ security?`${security.turnstileMode} · ${security.turnstileEnabled?'启用':'未启用'}`:'未获取' }}</dd></div></dl>
      <p class="technology-runtime-note">{{ graphError||meta?.notice||'人工核验事实以展示 API 的实际响应为准；模型未配置时不会伪装成已连接。' }}</p>
    </section>

    <section v-if="engineering" class="technology-quality"><div><p class="technology-section-kicker">ENGINEERING / VERIFIED RUN</p><h2>工程质量，有实际记录。</h2><p>最近验收：{{new Date(engineering.completedAt).toLocaleString('zh-CN')}}</p><strong>{{engineering.passed}} <small>项自动化测试通过</small></strong><p>{{engineering.scope}}。隔离测试记录不计入生产科学事实。</p></div><dl><div v-for="suite in engineering.suites" :key="suite.name"><dt>{{suite.name}}</dt><dd>{{suite.passed}} / PASS</dd></div><div><dt>失败 / 跳过</dt><dd>{{engineering.failed}} / {{engineering.skipped}}</dd></div></dl></section>
    <footer class="technology-footer"><span>VIRIDEXA <i>·</i> 植物科学数据平台</span><span>{{engineering?'测试为有时间戳的本地验收快照，数据统计由 API 独立读取。':'本轮自动化验收报告尚未读取。'}}</span><RouterLink to="/review">安全与审核说明 <UiIcon name="arrow" :size="14" /></RouterLink></footer>
  </main>
</template>
<style src="../styles/technology.css"></style>
