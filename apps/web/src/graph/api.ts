import { friendlyRateLimit } from '../security/api.ts'
export type SourceRef = { sourceId: string | number; displayLabel: string; sha256?: string; version?: string; licenseState?: string; locator?: Record<string, unknown> }
export type GraphNode = { id: string; nodeType: string; displayLabel: string; verificationState?: string; publicationState?: string; sourceReference?: SourceRef[]; attributes?: Record<string, unknown> }
export type GraphEdge = { id: string; source: string; target: string; relationType: string; displayLabel?: string; verificationState?: string; publicationState?: string; sourceReference?: SourceRef[] }
export type GraphPage = { nodes: GraphNode[]; edges: GraphEdge[]; centerId: string | null; nextCursor: string | null; hasMore: boolean; nodeLimit: number; edgeLimit: number; view: string; notice: string }
export type GraphMeta = { view: string; nodeTypes: string[]; relationTypes: string[]; nodeCounts: Record<string, number>; edgeCounts: Record<string, number>; publishedFactCount: number; notice: string }
const root = '/api/v1/graph'
const adminRoot = '/api/v1/admin/graph'
async function get<T>(path: string): Promise<T> {
  const response = await fetch(path, { credentials: 'include', headers: { Accept: 'application/json' } })
  if (!response.ok) throw Object.assign(new Error(response.status === 429 ? friendlyRateLimit(response.headers.get('Retry-After')||undefined) : response.status === 401 || response.status === 403 ? '管理员会话已失效或无图谱权限。' : `图谱请求失败（${response.status}）`),{status:response.status})
  return await response.json() as T
}
export const graphApi = {
  meta: (admin = false) => get<GraphMeta>(`${admin ? adminRoot : root}/meta`),
  search: (q: string, limit = 12, cursor?: string, admin = false) => { const p = new URLSearchParams({ q, limit: String(limit) }); if (cursor) p.set('cursor', cursor); return get<GraphPage>(`${admin ? adminRoot : root}/search?${p}`) },
  node: (id: string, admin = false) => get<GraphNode>(`${admin ? adminRoot : root}/nodes/${encodeURIComponent(id)}`),
  neighbors: (id: string, opts: { relationType?: string; nodeType?: string; verificationState?: string; publicationState?: string; limit?: number; cursor?: string } = {}, admin = false) => { const p = new URLSearchParams(); for (const [k, v] of Object.entries(opts)) if (v) p.set(k, String(v)); return get<GraphPage>(`${admin ? adminRoot : root}/nodes/${encodeURIComponent(id)}/neighbors?${p}`) },
}
