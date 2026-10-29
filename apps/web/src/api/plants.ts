import { friendlyRateLimit } from '../security/api.ts'
import type { PlantSearchResult } from '../types/plant'

export async function searchPlants(query: string, limit = 3, signal?: AbortSignal, afterId?:number): Promise<PlantSearchResult> {
  const params = new URLSearchParams({ limit: String(limit) })
  if(afterId) params.set('afterId',String(afterId))
  if (query.trim()) params.set('query', query.trim())

  const response = await fetch(`/api/v1/plants?${params.toString()}`, {
    headers: { Accept: 'application/json' }, signal,
  })

  if (!response.ok) {
    throw Object.assign(new Error(response.status===429?friendlyRateLimit(response.headers.get('Retry-After')||undefined):`植物资料暂时无法读取（${response.status}）。`),{status:response.status})
  }
  if (response.headers.get('content-type')?.includes('text/html')) {
    throw new Error('植物查询后端尚未连接；当前静态站点无法查询真实数据库。')
  }
  return response.json() as Promise<PlantSearchResult>
}
