<script setup lang="ts">
withDefaults(defineProps<{tone?:'info'|'warning'|'error';title?:string}>(),{tone:'info'})
</script>
<template><section class="state-notice" :class="`state-notice--${tone}`" :role="tone==='error'?'alert':'status'"><strong v-if="title">{{title}}</strong><slot/></section></template>
