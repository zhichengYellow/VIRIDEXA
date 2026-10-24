import type { PlantSearchResult } from '../types/plant'

export async function searchPlants(query: string, limit = 3): Promise<PlantSearchResult> {
  const params = new URLSearchParams({ limit: String(limit) })
  if (query.trim()) params.set('query', query.trim())

  const response = await fetch(`/api/v1/plants?${params.toString()}`, {
    headers: { Accept: 'application/json' },
  })

  if (!response.ok) {
    throw new Error(`植物资料暂时无法读取（${response.status}）。`)
  }
  return response.json() as Promise<PlantSearchResult>
}
