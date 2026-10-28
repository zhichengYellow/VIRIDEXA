<script setup lang="ts">defineProps<{kicker:string;title:string;description?:string}>()</script>
<template><header class="vx-page-header"><div><p class="vx-kicker">{{kicker}}</p><h1>{{title}}</h1><p v-if="description">{{description}}</p></div><div class="vx-page-actions"><slot/></div></header></template>
