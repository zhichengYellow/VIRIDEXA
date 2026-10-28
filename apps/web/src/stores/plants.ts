import { defineStore } from 'pinia'
import { ref } from 'vue'
import { searchPlants } from '../api/plants'
import type { PlantRecord } from '../types/plant'

export const usePlantsStore = defineStore('plants', () => {
  const query = ref('')
  const items = ref<PlantRecord[]>([])
  const total = ref(0)
  const nextAfterId=ref<number|undefined>()
  const loading = ref(false)
  const error = ref('')

  let controller: AbortController | undefined
  let generation = 0

  async function search(nextQuery = query.value, append=false) {
    controller?.abort()
    controller = new AbortController()
    const signal = controller.signal
    const current = ++generation
    query.value = nextQuery
    if(!append){items.value=[];total.value=0;nextAfterId.value=undefined}
    loading.value = true
    error.value = ''
    try {
      const result = await searchPlants(nextQuery, 6, signal, append?nextAfterId.value:undefined)
      if (current !== generation) return
      items.value = append?[...items.value,...result.items]:result.items
      nextAfterId.value=result.nextAfterId
      total.value = result.total
    } catch (cause) {
      if (current !== generation || signal.aborted) return
      error.value = cause instanceof Error ? cause.message : '植物资料暂时无法读取。'
      if(!append){items.value = [];total.value = 0}
    } finally {
      if (current === generation) loading.value = false
    }
  }

  function cancel(){generation++;controller?.abort();loading.value=false}

  return { cancel, query, items, total, loading, error, search, nextAfterId, loadMore:()=>search(query.value,true) }
})
