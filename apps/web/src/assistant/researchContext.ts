import type { AgentContext } from './api'

/** Build links using the query parameters each destination actually consumes. */
export function researchContextLinks(context: AgentContext) {
  const query = JSON.stringify(context)
  return {
    workbench: { path: '/workbench', query: { ...(context.plantId ? { plantId: String(context.plantId) } : {}), ...(context.sampleIds.length ? { sampleIds: context.sampleIds.join(',') } : {}), ...(context.sourceId ? { sourceId: context.sourceId } : {}), ...(context.kind ? { kind: context.kind } : {}), ...(context.plantPart ? { plantPart: context.plantPart } : {}) } },
    graph: { path: '/knowledge-graph', query: { ...(context.plantId ? { nodeId: `plant:${context.plantId}` } : {}) } },
    assistant: { path: '/assistant', query: { context: query } },
  }
}
