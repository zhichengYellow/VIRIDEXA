<script setup lang="ts">
import {computed} from 'vue'
const props=defineProps<{state?:string|null}>()
const labels:Record<string,string>={SOURCE_REPORTED:'来源记录',MACHINE_EXTRACTED:'自动提取',HUMAN_VERIFIED:'人工核验',PUBLIC:'公开',PUBLISHED:'正式发布',SOURCE_PUBLIC:'来源可浏览',METADATA_ONLY:'目录记录',CANDIDATE:'待核验',PENDING:'待审核',HELD:'保留待解析',UNRESOLVED:'身份未解',UNLINKED:'物种未关联',APPROVED:'已批准',REJECTED:'已拒绝',REVOKED:'已撤回',NEEDS_EVIDENCE:'待补充证据',VERIFIED_INTEGRITY:'文件摘要匹配',VERIFIED_FROM_PRINTED_HEADING:'原文标题已核对'}
const label=computed(()=>labels[props.state||'']||props.state||'状态未提供')
const tone=computed(()=>['HUMAN_VERIFIED','PUBLISHED','APPROVED'].includes(props.state||'')?'verified':['REJECTED','REVOKED','HELD'].includes(props.state||'')?'held':props.state==='MACHINE_EXTRACTED'?'machine':'source')
</script>
<template><span class="vx-status" :data-tone="tone"><span aria-hidden="true"></span>{{label}}</span></template>
