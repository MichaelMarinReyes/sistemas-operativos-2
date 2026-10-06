import { ref } from 'vue'
import { defineStore } from 'pinia'
import { request, errorMessage } from '../../shared/api/http'
import type { InventoryResponse, WarehouseResponse } from './types'

/**
 * Existencias y almacén.
 *
 * Se consulta por HTTP además del resumen que viaja por el canal de eventos: el
 * resumen del estado lleva totales y el detalle por producto y por ubicación es lo
 * que necesita la tabla y el mapa. La revisión publicada por el servidor permite saber si cambió
 * algo desde la última consulta.
 */
export const useInventoryStore = defineStore('inventory', () => {
  const inventory = ref<InventoryResponse | null>(null)
  const warehouse = ref<WarehouseResponse | null>(null)
  const loading = ref(false)
  const error = ref('')
  let requestVersion = 0

  /** Consulta ambas vistas y descarta la respuesta si mientras llegó otra más nueva. */
  async function load() {
    const version = ++requestVersion
    loading.value = true
    error.value = ''
    try {
      const [inventoryResponse, warehouseResponse] = await Promise.all([
        request<InventoryResponse>('/inventory'),
        request<WarehouseResponse>('/warehouse'),
      ])
      if (version !== requestVersion) return
      inventory.value = inventoryResponse
      warehouse.value = warehouseResponse
    } catch (cause) {
      if (version === requestVersion) error.value = errorMessage(cause)
    } finally {
      if (version === requestVersion) loading.value = false
    }
  }

  return { inventory, warehouse, loading, error, load }
})
