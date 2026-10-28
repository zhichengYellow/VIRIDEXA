import type { GraphEdge, GraphNode, GraphPage } from './api.ts'
export const NODE_LIMIT=120, EDGE_LIMIT=200
export class GraphState {
  readonly nodes = new Map<string,GraphNode>()
  readonly edges = new Map<string,GraphEdge>()
  readonly seeds = new Set<string>()
  readonly branches = new Map<string,{nodes:Set<string>;edges:Set<string>}>()
  limitReached=false
  clear(){this.nodes.clear();this.edges.clear();this.seeds.clear();this.branches.clear();this.limitReached=false}
  merge(page:GraphPage, centerId?:string){
    if(!centerId) page.nodes.forEach(n=>this.seeds.add(n.id))
    const branch=centerId?(this.branches.get(centerId)??{nodes:new Set<string>(),edges:new Set<string>()}):null
    for(const node of page.nodes){if(this.nodes.size>=NODE_LIMIT&&!this.nodes.has(node.id)){this.limitReached=true;continue}this.nodes.set(node.id,node);if(branch&&node.id!==centerId)branch.nodes.add(node.id)}
    for(const edge of page.edges){if(this.edges.size>=EDGE_LIMIT&&!this.edges.has(edge.id)){this.limitReached=true;continue}if(!this.nodes.has(edge.source)||!this.nodes.has(edge.target))continue;this.edges.set(edge.id,edge);branch?.edges.add(edge.id)}
    if(centerId&&branch)this.branches.set(centerId,branch)
  }
  collapse(centerId:string){const branch=this.branches.get(centerId);if(!branch)return;for(const id of branch.edges){const usedElsewhere=[...this.branches.entries()].some(([owner,b])=>owner!==centerId&&b.edges.has(id));if(!usedElsewhere)this.edges.delete(id)}for(const id of branch.nodes){const referenced=[...this.edges.values()].some(e=>e.source===id||e.target===id);if(!referenced&&!this.seeds.has(id)&&id!==centerId)this.nodes.delete(id)}this.branches.delete(centerId)}
}
export type PageContext={kind:'search';query:string}|{kind:'neighbors';nodeId:string;relationType:string;nodeType:string;verificationState:string;publicationState?:string}
export function matchesContext(a:PageContext,b:PageContext){return JSON.stringify(a)===JSON.stringify(b)}
