import test from 'node:test'
import assert from 'node:assert/strict'
import { GraphState, matchesContext } from '../state.ts'
import type { GraphNode,GraphEdge,GraphPage } from '../api.ts'
const node=(id:string):GraphNode=>({id,nodeType:'SOURCE',displayLabel:id})
const edge=(id:string,source:string,target:string):GraphEdge=>({id,source,target,relationType:'SOURCED_FROM'})
const page=(nodes:GraphNode[],edges:GraphEdge[]=[]):GraphPage=>({nodes,edges,centerId:null,nextCursor:null,hasMore:false,nodeLimit:120,edgeLimit:200,view:'public',notice:''})
test('graph merge is bounded at 120 nodes and 200 edges',()=>{const g=new GraphState();g.merge(page(Array.from({length:121},(_,i)=>node(String(i))),Array.from({length:201},(_,i)=>edge(String(i),'0','1'))));assert.equal(g.nodes.size,120);assert.equal(g.edges.size,200)})
test('collapse removes only its branch and retains shared nodes',()=>{const g=new GraphState();g.merge(page([node('seed')]));g.merge(page([node('shared'),node('leaf')],[edge('a','seed','shared'),edge('b','shared','leaf')]),'seed');g.merge(page([node('shared'),node('other')],[edge('c','seed','shared'),edge('d','shared','other')]),'seed2');g.collapse('seed');assert.equal(g.nodes.has('leaf'),false);assert.equal(g.nodes.has('shared'),true);assert.equal(g.edges.has('c'),true)})
test('pagination contexts distinguish search from selected-node neighbors and filters',()=>{assert.equal(matchesContext({kind:'search',query:'fern'},{kind:'neighbors',nodeId:'n1',relationType:'',nodeType:'',verificationState:''}),false);assert.equal(matchesContext({kind:'neighbors',nodeId:'n1',relationType:'',nodeType:'',verificationState:''},{kind:'neighbors',nodeId:'n2',relationType:'',nodeType:'',verificationState:''}),false)})
test('cyclic relations merge once and do not recursively expand',()=>{const g=new GraphState();g.merge(page([node('a')]));g.merge(page([node('a'),node('b')],[edge('ab','a','b'),edge('ba','b','a')]),'a');assert.equal(g.nodes.size,2);assert.equal(g.edges.size,2);g.collapse('a');assert.equal(g.edges.size,0);assert.equal(g.nodes.size,1)})

test('mode switch clear removes previously loaded graph state',()=>{const g=new GraphState();g.merge(page([node('private')],[edge('rel','private','private')]));g.clear();assert.equal(g.nodes.size,0);assert.equal(g.edges.size,0);assert.equal(g.branches.size,0)})

test('collapse keeps an identical relation while another expanded branch references it',()=>{const g=new GraphState();g.merge(page([node('a'),node('b'),node('c')]));const shared=edge('shared','a','b');g.merge(page([node('b')],[shared]),'a');g.merge(page([node('b')],[shared]),'c');g.collapse('a');assert.equal(g.edges.has('shared'),true);g.collapse('c');assert.equal(g.edges.has('shared'),false)})

test('neighbor cursor is valid only for the selected node and active filters',()=>{const saved={kind:'neighbors' as const,nodeId:'source-a',relationType:'SOURCE_FILE',nodeType:'FILE',verificationState:'HASH_MATCH'};assert.equal(matchesContext(saved,{...saved}),true);assert.equal(matchesContext(saved,{...saved,nodeId:'source-b'}),false);assert.equal(matchesContext(saved,{...saved,relationType:''}),false)})
