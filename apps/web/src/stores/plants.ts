import { defineStore } from 'pinia'
import { ref } from 'vue'
import { searchPlants } from '../api/plants'
import type { PlantRecord } from '../types/plant'

export const usePlantsStore = defineStore('plants', () => {
  const query = ref('')
  const items = ref<PlantRecord[]>([])
  const total = ref(0)
  const loading = ref(false)
  const error = ref('')

  async function search(nextQuery = query.value) {
    query.value = nextQuery
    loading.value = true
    error.value = ''
    try {
      const result = await searchPlants(nextQuery)
      items.value = result.items
      total.value = result.total
    } catch (cause) {
      error.value = cause instanceof Error ? cause.message : '植物资料暂时无法读取。'
      items.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  return { query, items, total, loading, error, search }
})
