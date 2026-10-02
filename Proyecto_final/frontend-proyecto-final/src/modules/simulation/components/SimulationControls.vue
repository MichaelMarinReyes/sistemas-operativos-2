<script setup lang="ts">
import { ref } from 'vue'
import { request, errorMessage } from '../../../shared/api/http'
import { useStatusStore } from '../../status/store'
import type { SimulationStatus } from '../../status/types'

const store = useStatusStore()
const sending = ref(false)
const error = ref('')

async function command(action: 'start' | 'stop') {
  if (sending.value) return
  sending.value = true
  error.value = ''
  try {
    await request<{ status: SimulationStatus }>(`/simulation/${action}`, { method: 'POST' })
    await store.refresh()
  } catch (cause) { error.value = errorMessage(cause) }
  finally { sending.value = false }
}
</script>

<template>
  <section class="panel simulation-controls" aria-labelledby="simulation-heading">
    <div class="section-heading">
      <div>
        <h2 id="simulation-heading">Motor de simulación</h2>
        <p class="muted">Preparación → escaneo → carga. El inventario se integrará en los próximos hitos.</p>
      </div>
      <div class="control-buttons">
        <button :disabled="sending || !store.status || store.status.simulationStatus === 'RUNNING' || store.status.simulationStatus === 'STOPPING'" @click="command('start')">Iniciar simulación</button>
        <button class="secondary" :disabled="sending || !store.status || !['RUNNING', 'STOPPING'].includes(store.status.simulationStatus)" @click="command('stop')">Detener simulación</button>
      </div>
    </div>
    <p class="muted">Detener interrumpe los pedidos activos, los marca con error y libera sus recursos. Los pedidos aún no seleccionados permanecen pendientes.</p>
    <p v-if="sending" role="status">Aplicando la acción…</p>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
  </section>
</template>
