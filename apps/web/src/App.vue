<script setup lang="ts">
import {RouterView} from 'vue-router'
import BackendNotice from './components/BackendNotice.vue'
import AppShell from './components/AppShell.vue'
</script>
<template><AppShell><BackendNotice/><RouterView/></AppShell></template>
