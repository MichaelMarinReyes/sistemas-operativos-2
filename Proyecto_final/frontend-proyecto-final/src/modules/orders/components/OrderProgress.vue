<script setup lang="ts">
import type { Order } from '../types'
import { orderStageLabels } from '../types'
defineProps<{ order: Order }>()
</script>

<template>
  <div class="order-progress">
    <div class="section-heading">
      <span>{{ orderStageLabels[order.stage] }}</span>
      <strong>{{ order.progress.toFixed(1) }} %</strong>
    </div>
    <progress :value="order.progress" max="100" :aria-label="`Progreso del pedido ${order.id}`" />
    <p class="muted">Trabajo restante estimado:
      {{ order.estimatedRemainingMinutes === null ? 'Pendiente de asignación de recursos' : `${order.estimatedRemainingMinutes.toFixed(2)} minutos simulados` }}.
      <span v-if="order.status !== 'COMPLETED' && order.status !== 'ERROR'">No incluye futuras esperas por recursos.</span>
    </p>
    <p v-if="order.errorMessage" class="notice error">{{ order.errorMessage }}</p>
  </div>
</template>
