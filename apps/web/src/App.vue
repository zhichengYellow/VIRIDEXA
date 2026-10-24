<script setup lang="ts">
import { RouterView } from 'vue-router'
</script>

<template>
  <div class="min-h-screen">
    <header class="site-header">
      <a class="brand" href="/" aria-label="VIRIDEXA 植序首页">
        <span class="brand-mark" aria-hidden="true">
          <svg viewBox="0 0 40 40" fill="none">
            <path d="M20 32V16" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
            <path d="M20 21C10.7 21 7 16.1 7 8c8.7 0 13 4.5 13 13Z" fill="currentColor" fill-opacity=".15" stroke="currentColor" stroke-width="1.5" />
            <path d="M20 27c9 0 13-4.5 13-12-8.5 0-13 4.2-13 12Z" fill="currentColor" fill-opacity=".22" stroke="currentColor" stroke-width="1.5" />
            <path d="M8 33h24" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" />
          </svg>
        </span>
        <span class="brand-copy">
          <span class="brand-name">VIRIDEXA <i>·</i> 植序</span>
          <span class="brand-caption">BOTANICAL INTELLIGENCE</span>
        </span>
      </a>
      <div class="header-right">
        <span class="environment-label"><span class="status-dot"></span>研究预览</span>
        <span class="header-divider"></span>
        <span class="tagline">Decode Nature. Connect Knowledge.</span>
      </div>
    </header>
    <RouterView />
    <footer class="site-footer">
      <span>VIRIDEXA · 植序</span>
      <span>让每一条植物知识，都能回到它的证据。</span>
    </footer>
  </div>
</template>
