import { defineStore } from 'pinia'
import { ref } from 'vue'
import { errorMessage, request } from '../../shared/api/http'
import type { Product } from './types'

export const useCatalogStore = defineStore('catalog', () => {
  const products = ref<Product[]>([])
  const loading = ref(false)
  const error = ref('')

  async function load() {
    if (loading.value) return
    loading.value = true
    error.value = ''
    try { products.value = await request<Product[]>('/products') }
    catch (cause) { error.value = errorMessage(cause) }
    finally { loading.value = false }
  }

  return { products, loading, error, load }
})
