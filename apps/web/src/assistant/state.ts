import type { RunView } from './api'
export const HISTORY_KEY='viridexa.assistant.history'
export function readHistory(storage:Pick<Storage,'getItem'>=localStorage):string[]{try{const value=JSON.parse(storage.getItem(HISTORY_KEY)||'[]');return Array.isArray(value)?value.filter((x):x is string=>typeof x==='string').slice(0,8):[]}catch{return []}}
export function saveHistory(question:string,storage:Pick<Storage,'getItem'|'setItem'>=localStorage):string[]{const next=[question,...readHistory(storage).filter(q=>q!==question)].slice(0,8);storage.setItem(HISTORY_KEY,JSON.stringify(next));return next}
export function clearHistory(storage:Pick<Storage,'removeItem'>=localStorage){storage.removeItem(HISTORY_KEY)}
export function terminal(status:RunView['status']){return ['COMPLETED','DEGRADED','CANCELLED','TIMED_OUT','FAILED'].includes(status)}
export function safeEvidenceHref(href:string){try{const origin=typeof location==='undefined'?'http://localhost':location.origin;const u=new URL(href,origin);return u.origin===origin&&u.pathname.startsWith('/api/v1/')?u.pathname+u.search+u.hash:null}catch{return null}}
