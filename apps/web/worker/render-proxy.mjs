// Optional same-origin bridge only. No database, model or authorization logic.
const jsonError=(status,code)=>Response.json({code},{status,headers:{'Cache-Control':'no-store'}})
export default {
 async fetch(request,env) {
  const url=new URL(request.url)
  if (!url.pathname.startsWith('/api/')) return env.ASSETS.fetch(request)
  if (!['GET','HEAD','POST','OPTIONS'].includes(request.method)) return jsonError(405,'METHOD_NOT_ALLOWED')
  let upstream
  try {
   upstream=new URL(env.API_ORIGIN)
   if(upstream.protocol!=='https:'||!/^[-a-z0-9]+\.onrender\.com$/.test(upstream.hostname)||upstream.port||upstream.username||upstream.password||upstream.pathname!=='/'||upstream.search||upstream.hash)throw Error()
  } catch {return jsonError(503,'BACKEND_NOT_CONFIGURED')}
  const headers=new Headers(request.headers)
  for(const key of [...headers.keys()]) {
   if(['host','forwarded','connection','content-length'].includes(key)||key.startsWith('x-forwarded-')||key.startsWith('cf-')||key.startsWith('x-viridexa-')) headers.delete(key)
  }
  headers.set('Cache-Control','no-store')
  let body
  if(request.body){
   const reader=request.body.getReader(),chunks=[];let bytes=0
   for(;;){const {value,done}=await reader.read();if(done)break;bytes+=value.byteLength;if(bytes>65536){await reader.cancel();return jsonError(413,'REQUEST_TOO_LARGE')}chunks.push(value)}
   body=new Uint8Array(bytes);let offset=0;for(const chunk of chunks){body.set(chunk,offset);offset+=chunk.byteLength}
  }
  try {
   const response=await fetch(upstream.origin+url.pathname+url.search,{method:request.method,headers,body,redirect:'manual',signal:AbortSignal.timeout(25000)})
   // Never follow upstream redirects with a user's session cookie.
   if(response.status>=300&&response.status<400)return jsonError(502,'BACKEND_REDIRECT_REFUSED')
   const safeHeaders=new Headers(response.headers)
   safeHeaders.set('Cache-Control','no-store');safeHeaders.set('X-Content-Type-Options','nosniff');safeHeaders.delete('server');safeHeaders.delete('x-powered-by')
   return new Response(response.body,{status:response.status,headers:safeHeaders})
  } catch {return jsonError(504,'BACKEND_UNAVAILABLE_OR_WAKING')}
 }
}
