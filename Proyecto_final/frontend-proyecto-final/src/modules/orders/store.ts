import { defineStore } from 'pinia'
import { ref } from 'vue'
import { errorMessage } from '../../shared/api/http'
import { fetchOrders } from './api'
import type { OrderPage } from './types'

export const useOrdersStore = defineStore('orders', () => {
  const result = ref<OrderPage>({ items: [], page: 0, size: 10, total: 0 })
  const loading = ref(false)
  const error = ref('')

  // La última consulta solicitada prevalece, incluso si una respuesta anterior llega después.
  let requestVersion = 0
  async function load(page = 0) {
    const version = ++requestVersion
    loading.value = true
    error.value = ''
    try {
      const response = await fetchOrders(page)
      if (version === requestVersion) result.value = response
    } catch (cause) {
      if (version === requestVersion) error.value = errorMessage(cause)
    } finally {
      if (version === requestVersion) loading.value = false
    }
  }

  return { result, loading, error, load }
})
