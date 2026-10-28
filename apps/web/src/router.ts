import { createRouter, createWebHistory } from 'vue-router'
import { useAccount } from './stores/account'
import HomeView from './views/HomeView.vue'

export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', name: 'home', component: HomeView },
    { path: '/explore', name: 'explore', component: () => import('./views/AtlasView.vue') },
    { path: '/login', name: 'login', component: () => import('./views/AuthView.vue') },
    { path: '/register', name: 'register', component: () => import('./views/AuthView.vue') },
    { path: '/account', name: 'account', meta: { requiresAuth: true }, component: () => import('./views/AccountView.vue') },
    { path: '/review', name: 'review', component: () => import('./views/ReviewView.vue') },
    { path: '/workbench', name: 'workbench', meta: { requiresAuth: true }, component: () => import('./views/WorkbenchView.vue') },
    { path: '/plants/:id', name: 'plant-detail', component: () => import('./views/PlantDetailView.vue') },
    { path: '/assistant', name: 'assistant', meta: { requiresAuth: true }, component: () => import('./views/AssistantView.vue') },
    { path: '/knowledge-graph', name: 'knowledge-graph', component: () => import('./views/KnowledgeGraphView.vue') },
    { path: '/technology', name: 'technology', component: () => import('./views/TechnologyView.vue') },
    { path: '/:pathMatch(.*)*', component: () => import('./views/NotFoundView.vue') },
  ],
  scrollBehavior: to => to.hash ? { el: to.hash, top: 132, behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth' } : { top: 0 },
})

router.beforeEach(async to => {
  if (!to.meta.requiresAuth) return
  const account = useAccount()
  await account.refresh()
  if (!account.profile) return { path: '/login', query: { redirect: to.fullPath } }
})

router.afterEach((to) => {
  const names: Record<string, string> = { home:'植物探索', explore:'植物图鉴', login:'账户登录', register:'创建账户', account:'我的账户', review:'证据审核', workbench:'科研实验台', 'plant-detail':'植物资料', assistant:'研究助手', 'knowledge-graph':'知识图谱', technology:'技术与知识边界' }
  document.title = `${names[String(to.name)] ?? '页面未找到'} · VIRIDEXA 植序`
})

// A tab opened before a deployment may reference a retired route chunk.
// Retry the destination through the current HTML once; never loop on a real network outage.
router.onError((error, to) => {
  if (!/dynamically imported module|module script|Importing a module script/i.test(String(error))) return
  const key = `viridexa.chunk-retry:${to.fullPath}`
  if (sessionStorage.getItem(key)) return
  sessionStorage.setItem(key, '1')
  window.location.assign(to.fullPath)
})
