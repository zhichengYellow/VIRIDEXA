import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { accountApi, AccountError, type Profile } from '../account/api'
export const useAccount = defineStore('account',()=>{
 const profile=ref<Profile|null>(null),ready=ref(false),busy=ref(false),error=ref('')
 const isAdmin=computed(()=>profile.value?.roles.includes('ADMIN')??false)
 let version=0
 let pending:Promise<void>|null=null
 async function refresh(){if(pending)return pending;const current=version;pending=(async()=>{try{const data=await accountApi.session();if(current!==version)return;profile.value=data;error.value=''}catch(e){if(current!==version)return;if(e instanceof AccountError&&(e.status===401||e.status===403)){profile.value=null;error.value=''}else error.value=(e as Error).message}finally{ready.value=true;pending=null}})();return pending}
 async function login(email:string,password:string,turnstileToken?:string){busy.value=true;version++;try{profile.value=await accountApi.login(email.trim(),password,turnstileToken);ready.value=true;error.value=''}finally{busy.value=false}}
 async function register(email:string,name:string,password:string,turnstileToken?:string){busy.value=true;version++;try{profile.value=await accountApi.register(email.trim(),name.trim(),password,turnstileToken);ready.value=true;error.value=''}finally{busy.value=false}}
 async function logout(){busy.value=true;version++;try{await accountApi.logout();profile.value=null;error.value=''}catch(e){if(e instanceof AccountError&&e.status===401){profile.value=null;error.value=''}else throw e}finally{busy.value=false}}
 return {profile,ready,busy,error,isAdmin,refresh,login,register,logout}
})
