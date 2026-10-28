export const GUIDE_KEY = 'viridexa.guide.v1'
export const STEP_COUNT = 5
export type Progress = { step: number; hidden: boolean; completed: boolean; active: boolean }
export const freshProgress = (): Progress => ({ step: 0, hidden: false, completed: false, active: false })
export function parseProgress(value: string | null): Progress {
  try {
    if (!value || value.length > 300) return freshProgress()
    const p: unknown = JSON.parse(value)
    if (!p || typeof p !== 'object') return freshProgress()
    const r = p as Record<string, unknown>
    if (!Number.isInteger(r.step) || Number(r.step) < 0 || Number(r.step) >= STEP_COUNT ||
      ['hidden', 'completed', 'active'].some(k => typeof r[k] !== 'boolean')) return freshProgress()
    return { step: Number(r.step), hidden: Boolean(r.hidden), completed: Boolean(r.completed), active: Boolean(r.active) && !r.completed }
  } catch { return freshProgress() }
}
/** URL context is a hint only; the public API must authorize the actual record. */
export function contextPlantId(path: string, query: Record<string, unknown>): number | null {
  const detail = /^\/plants\/([1-9][0-9]*)$/.exec(path)?.[1]
  const graph = typeof query.nodeId === 'string' ? /^plant:([1-9][0-9]*)$/.exec(query.nodeId)?.[1] : undefined
  let assistant: string | undefined
  if (typeof query.context === 'string' && query.context.length <= 2048) {
    try { const context: unknown = JSON.parse(query.context); if (context && typeof context === 'object' && 'plantId' in context && typeof context.plantId === 'number' && Number.isSafeInteger(context.plantId) && context.plantId > 0) assistant = String(context.plantId) } catch { /* Malformed URL context is ignored. */ }
  }
  const value = detail ?? graph ?? query.plantId ?? assistant
  if (typeof value !== 'string' || !/^[1-9][0-9]*$/.test(value)) return null
  const n = Number(value)
  return Number.isSafeInteger(n) ? n : null
}
