import { ref } from 'vue'
import { defineStore } from 'pinia'
import { request, errorMessage, apiBaseUrl } from '../../shared/api/http'
import type { StatusResponse } from './types'

/** Versión del contrato que esta interfaz entiende; si cambia, hay que actualizar la aplicación. */
const supportedContractVersion = 5

export const useStatusStore = defineStore('status', () => {
  const status = ref<StatusResponse | null>(null)
  const loading = ref(false)
  const error = ref('')
  const connection = ref<'DISCONNECTED' | 'CONNECTING' | 'LIVE' | 'RECONNECTING'>('DISCONNECTED')
  const streamError = ref('')
  let source: EventSource | null = null
  let requestVersion = 0

  function accept(snapshot: StatusResponse) {
    if (status.value?.runId === snapshot.runId && status.value.sequence >= snapshot.sequence) return
    status.value = snapshot
  }

  async function refresh() {
    const version = ++requestVersion
    const previousRun = status.value?.runId
    loading.value = true
    error.value = ''
    try {
      const response = await request<StatusResponse>('/status')
      if (version === requestVersion && !(status.value?.runId !== previousRun && status.value?.runId !== response.runId)) accept(response)
    } catch (cause) {
      if (version === requestVersion) error.value = errorMessage(cause)
    } finally {
      if (version === requestVersion) loading.value = false
    }
  }

  function connect() {
    if (source) return
    connection.value = 'CONNECTING'
    const current = new EventSource(`${apiBaseUrl}/events`)
    source = current
    current.addEventListener('status', event => {
      if (source !== current) return
      try {
        const snapshot = JSON.parse((event as MessageEvent<string>).data) as StatusResponse
        if (snapshot.contractVersion !== supportedContractVersion || !snapshot.runId || !Number.isFinite(snapshot.sequence)) {
          streamError.value = 'El formato de las actualizaciones no es compatible. Actualiza la aplicación.'
          return
        }
        accept(snapshot)
        connection.value = 'LIVE'
        streamError.value = ''
        error.value = ''
      } catch { streamError.value = 'No se pudo leer la actualización del servidor.' }
    })
    current.onerror = () => {
      if (source !== current) return
      connection.value = 'RECONNECTING'
      streamError.value = 'Se perdió la conexión en tiempo real. Reconectando automáticamente…'
    }
  }

  function disconnect() {
    source?.close()
    source = null
    connection.value = 'DISCONNECTED'
  }

  return { status, loading, error, connection, streamError, refresh, connect, disconnect }
})
