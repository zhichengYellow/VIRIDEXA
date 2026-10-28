<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref, watch } from 'vue'
const props=withDefaults(defineProps<{siteKey:string;action:'login'|'register';resetKey?:number}>(),{resetKey:0})
const emit=defineEmits<{token:[value:string];error:[]}>()
const host=ref<HTMLElement|null>(null),loading=ref(true),message=ref('正在加载安全验证…')
let generation=0
let widget:string|undefined,script:HTMLScriptElement|undefined,disposed=false,timer:ReturnType<typeof setTimeout>|undefined
function clear(){generation++;if(timer)clearTimeout(timer);timer=undefined;if(widget&&window.turnstile){try{window.turnstile.remove(widget)}catch{}}widget=undefined}
function fail(){clear();if(!window.turnstile){script?.remove();script=undefined}loading.value=false;message.value='安全验证暂不可用，请稍后重试。';emit('error')}
function render(){if(disposed||!host.value)return;clear();const current=generation;loading.value=true;message.value='正在加载安全验证…';timer=setTimeout(()=>{if(current===generation)fail()},15000);const ready=()=>{if(disposed||current!==generation||!host.value||!window.turnstile)return;try{widget=window.turnstile.render(host.value,{sitekey:props.siteKey,action:props.action,size:host.value.clientWidth<300?'compact':'flexible',callback:(token:string)=>{if(disposed||current!==generation)return;if(timer)clearTimeout(timer);loading.value=false;message.value='';emit('token',token)},'expired-callback':()=>{if(disposed||current!==generation)return;emit('token','');message.value='验证已过期，请重新完成验证。'},'error-callback':()=>{if(!disposed&&current===generation)fail()}});}catch{fail()}};if(window.turnstile){ready();return}script=document.querySelector<HTMLScriptElement>('script[data-viridexa-turnstile]')??document.createElement('script');if(!script.isConnected){script.src='https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit';script.async=true;script.defer=true;script.dataset.viridexaTurnstile='true';document.head.append(script)}script.addEventListener('load',ready,{once:true});script.addEventListener('error',fail,{once:true})}
onMounted(render);watch(()=>props.resetKey,()=>render());onBeforeUnmount(()=>{disposed=true;clear()})
declare global{interface Window{turnstile?:{render:(el:HTMLElement,opts:Record<string,unknown>)=>string;remove:(id:string)=>void}}}
</script>
<template><div class="turnstile-challenge"><div ref="host" class="turnstile-host"/><p v-if="loading||message" role="status">{{message}}</p><button v-if="!loading&&message" type="button" class="plain-button" @click="render">重新加载安全验证</button></div></template>
