<script setup lang="ts">
import { computed, ref } from 'vue'
import { useOrdersStore } from '../store'
import { useStatusStore } from '../../status/store'
import OrderProgress from './OrderProgress.vue'
import { orderStatusLabels } from '../types'
import { merchandiseLabels } from '../../catalog/types'
import { serviceLevelLabel } from '../../../shared/serviceLevels'

const store = useOrdersStore()
const status = useStatusStore()

const filter = ref<'todos' | 'asignados' | 'pendientes'>('todos')

const liveOrders = computed(() => store.result.items.map(order =>
  status.status?.activeOrders.find(active => active.id === order.id) || order))

const filteredOrders = computed(() => {
  let list = [...liveOrders.value]
  if (filter.value === 'asignados') {
    list = list.filter((order: any) => !!order.assignedWorkerId)
  } else if (filter.value === 'pendientes') {
    list = list.filter(order => order.status === 'QUEUED' || order.status === 'WAITING_STOCK')
  }
  return list
})
</script>

<template>
  <section class="panel order-list" aria-labelledby="orders-heading" :aria-busy="store.loading">
    <div class="section-heading">
      <h2 id="orders-heading">Pedidos registrados ({{ store.result.total }})</h2>
      <button class="secondary" :disabled="store.loading" @click="store.load(store.result.page)">Actualizar pedidos</button>
    </div>
    <p class="muted">Los más recientes aparecen primero. El progreso se actualiza en tiempo real mientras el motor está activo.</p>

    <div class="orders-filter">
      <label for="orders-filter-select">Filtrar pedidos</label>
      <select id="orders-filter-select" v-model="filter">
        <option value="todos">Todos los pedidos</option>
        <option value="asignados">Pedidos asignados a trabajadores</option>
        <option value="pendientes">Pedidos pendientes de selección</option>
      </select>
    </div>

    <p v-if="store.error" class="notice error" role="alert">{{ store.error }}</p>
    <p v-if="store.loading" role="status">Cargando pedidos…</p>
    <p v-else-if="!filteredOrders.length && !store.error">No hay pedidos que coincidan con el filtro seleccionado.</p>

    <div class="table-scroll">
      <div v-for="order in filteredOrders" :key="order.id" class="order-card">
        <div class="section-heading">
          <h3>{{ order.customerName }} · {{ serviceLevelLabel(order.serviceLevel) }}</h3>
          <span class="badge">{{ orderStatusLabels[order.status] }}</span>
        </div>
        <p class="order-id">Pedido {{ order.id }}</p>
        <p v-if="(order as any).assignedWorkerId" class="muted">Asignado a: {{ (order as any).assignedWorkerId }}</p>
        <ul>
          <li v-for="line in order.lines" :key="line.productId">
            {{ line.productName }} · {{ merchandiseLabels[line.merchandiseType] }} · {{ line.quantity }} unidades
          </li>
        </ul>
        <p><strong>Total: {{ order.totalUnits }} unidades</strong> · {{ new Date(order.createdAt).toLocaleString('es-GT') }}</p>
        <OrderProgress :order="order" />
      </div>
    </div>

    <nav class="pagination" aria-label="Páginas de pedidos">
      <button class="secondary" :disabled="store.loading || store.result.page === 0" @click="store.load(store.result.page - 1)">Anterior</button>
      <span>Página {{ store.result.page + 1 }} de {{ Math.max(1, Math.ceil(store.result.total / store.result.size)) }}</span>
      <button class="secondary" :disabled="store.loading || (store.result.page + 1) * store.result.size >= store.result.total" @click="store.load(store.result.page + 1)">Siguiente</button>
    </nav>
  </section>
</template>

<style scoped>
.orders-filter {
  display: flex;
  flex-direction: column;
  gap: 4px;
  max-width: 320px;
  margin: 12px 0;
}

.orders-filter label {
  font-size: 0.9rem;
  color: #334155;
}

.orders-filter select {
  padding: 7px 10px;
  border-radius: 8px;
  border: 1px solid #cbd5e1;
  background: #ffffff;
}
</style>
