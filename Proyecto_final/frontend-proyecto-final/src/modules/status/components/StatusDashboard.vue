<script setup lang="ts">
import { useStatusStore } from '../store'
import { resourceLabels, simulationStatusLabels } from '../types'
import type { ResourceType } from '../types'
import { orderStatusLabels } from '../../orders/types'
import OrderProgress from '../../orders/components/OrderProgress.vue'
import { serviceLevelLabel } from '../../../shared/serviceLevels'
const store = useStatusStore()

const connectionLabels = { DISCONNECTED: 'Sin conexión en tiempo real', CONNECTING: 'Conectando…', LIVE: 'Actualización en vivo', RECONNECTING: 'Reconectando…' }
</script>

<template>
  <div v-if="store.error" class="notice error" role="alert">
    {{ store.error }} <span v-if="store.status">Se muestran los últimos datos recibidos.</span>
  </div>
  <p v-else-if="store.loading" class="notice" role="status">Consultando el estado del centro…</p>
  <p v-if="store.streamError" class="notice error" role="status">{{ store.streamError }}</p>
  <template v-if="store.status">
    <section class="connection" aria-label="Estado del sistema">
      <span :class="['badge', { stale: store.connection !== 'LIVE' }]">{{ connectionLabels[store.connection] }}</span>
      <span>{{ simulationStatusLabels[store.status.simulationStatus] }}</span>
      <span class="muted">Última lectura: {{ new Date(store.status.generatedAt).toLocaleTimeString('es-GT') }}</span>
      <span class="muted">Reloj: {{ store.status.elapsedMinutes.toFixed(2) }} min simulados · Escala {{ store.status.timeScale }}× · {{ store.status.workerCount }} trabajadores</span>
    </section>
    <section class="stats" aria-label="Resumen de pedidos">
      <article><span>En espera</span><strong>{{ store.status.orders.waiting }}</strong></article>
      <article><span>En proceso</span><strong>{{ store.status.orders.processing }}</strong></article>
      <article><span>Completados</span><strong>{{ store.status.orders.completed }}</strong></article>
      <article><span>Con error</span><strong>{{ store.status.orders.failed }}</strong></article>
    </section>
    <section aria-labelledby="resources-heading">
      <h2 id="resources-heading">Recursos del centro</h2>
      <div class="resources">
        <article v-for="resource in store.status.resources" :key="resource.type" class="resource">
          <h3>{{ resource.name }}</h3>
          <p><strong>{{ resource.capacity - resource.inUse }}</strong> disponibles de {{ resource.capacity }}</p>
          <meter :value="resource.inUse" :max="resource.capacity" min="0" :aria-label="`Ocupación de ${resource.name}`" />
          <span class="muted">{{ resource.inUse }} en uso</span>
          <ul class="resource-instances">
            <li v-for="(instance, index) in resource.instances" :key="instance.id" :class="{ occupied: instance.ownerId }">
              <span>{{ resourceLabels[resource.type] }} {{ index + 1 }}</span>
              <span v-if="instance.ownerId" :title="instance.ownerId">Pedido {{ instance.ownerId.slice(0, 8) }}…
                <small>Desde {{ new Date(instance.acquiredAt!).toLocaleTimeString('es-GT') }}</small>
              </span>
              <span v-else>Disponible</span>
            </li>
          </ul>
        </article>
      </div>
    </section>
    <section class="panel active-orders" aria-labelledby="active-heading">
      <h2 id="active-heading">Pedidos asignados a trabajadores</h2>
      <p v-if="!store.status.activeOrders.length" class="muted">No hay pedidos activos.</p>
      <article v-for="order in store.status.activeOrders" :key="order.id" class="order-card">
        <div class="section-heading"><h3>{{ order.customerName }} · {{ serviceLevelLabel(order.serviceLevel) }}</h3><span class="badge">{{ orderStatusLabels[order.status] }}</span></div>
        <p class="order-id">Pedido {{ order.id }}</p>
        <OrderProgress :order="order" />
        <p v-for="entry in store.status.resourceRequests.filter(entry => entry.orderId === order.id)" :key="entry.orderId" class="muted">
          Esperando: {{ Object.entries(entry.required).map(([type, count]) => `${count} ${resourceLabels[type as ResourceType]}`).join(', ') }}.
        </p>
      </article>
    </section>
    <section class="panel waiting-orders" aria-labelledby="waiting-heading">
      <h2 id="waiting-heading">Pendientes de selección</h2>
      <p class="muted">Prioridad estricta por nivel; desempate por llegada. Se muestran hasta 50 pedidos. Los trabajadores ya asignados compiten por recursos sin expropiación.</p>
      <p v-if="!store.status.waitingOrders.length">No hay pedidos pendientes de selección.</p>
      <ol><li v-for="order in store.status.waitingOrders" :key="order.id">{{ order.customerName }} · {{ serviceLevelLabel(order.serviceLevel) }} · {{ order.totalUnits }} unidades <span class="order-id">({{ order.id }})</span></li></ol>
    </section>
    <section class="panel warehouse-panel">
      <h2>Almacén</h2>
      <p>{{ store.status.warehouse.locations }} ubicaciones · {{ store.status.warehouse.volumePerLocation }} unidades de volumen por ubicación</p>
      <p class="muted">Volumen ocupado: {{ store.status.warehouse.occupiedVolume }} / {{ store.status.warehouse.locations * store.status.warehouse.volumePerLocation }}</p>
    </section>
  </template>
</template>
