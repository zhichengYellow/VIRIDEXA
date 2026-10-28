import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { GUIDE_KEY, freshProgress, parseProgress, STEP_COUNT } from '../onboarding/progress'
export const useOnboarding = defineStore('onboarding', () => {
  let saved = freshProgress()
  try { saved = parseProgress(localStorage.getItem(GUIDE_KEY)) } catch { /* Storage may be unavailable. */ }
  const progress = ref(saved), open = ref(false)
  const showWelcome = computed(() => !progress.value.hidden && !progress.value.completed)
  function save() { try { localStorage.setItem(GUIDE_KEY, JSON.stringify(progress.value)) } catch { /* Guide remains usable in memory. */ } }
  function begin() { progress.value = { step: 0, hidden: true, completed: false, active: true }; open.value = true; save() }
  function resume() { open.value = true }
  function select(step: number) { if (Number.isInteger(step) && step >= 0 && step < STEP_COUNT) { progress.value.step = step; save() } }
  function dismiss() { open.value = false; progress.value.hidden = true; progress.value.active = false; save() }
  function visit() { open.value = false; if (progress.value.step === STEP_COUNT - 1) { progress.value.completed = true; progress.value.active = false } else { progress.value.step++; progress.value.active = true }; save() }
  return { progress, open, showWelcome, begin, resume, select, dismiss, visit }
})
