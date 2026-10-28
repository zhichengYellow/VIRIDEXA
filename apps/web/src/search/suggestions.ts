export type Suggestion={id:number;label:string;scientificName:string|null;matchedName:string;matchType:string}
export async function suggestions(query:string,signal?:AbortSignal):Promise<Suggestion[]>{
 const q=query.trim();if(!q||q.length>80)return []
 const r=await fetch('/api/v1/plants/suggestions?'+new URLSearchParams({query:q,limit:'6'}),{signal,headers:{Accept:'application/json'},cache:'no-store'})
 if(!r.ok)throw new Error(r.status===429?'输入较频繁，请稍后再试。':'联想暂不可用，仍可直接搜索。')
 return r.json() as Promise<Suggestion[]>
}
