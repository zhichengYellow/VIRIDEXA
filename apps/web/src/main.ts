import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import { router } from './router'
import './styles/tokens.css'
import './styles/public.css'
import './styles/account-system.css'

createApp(App).use(createPinia()).use(router).mount('#app')

import './styles/immersive.css'
