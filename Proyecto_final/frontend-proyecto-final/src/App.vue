<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, watch } from 'vue'
import SimulationControls from './modules/simulation/components/SimulationControls.vue'
import CustomerForm from './modules/customers/components/CustomerForm.vue'
import OrderForm from './modules/orders/components/OrderForm.vue'
import OrderList from './modules/orders/components/OrderList.vue'
import StatusDashboard from './modules/status/components/StatusDashboard.vue'
import { useCustomersStore } from './modules/customers/store'
import { useCatalogStore } from './modules/catalog/store'
import { useOrdersStore } from './modules/orders/store'
import { useStatusStore } from './modules/status/store'

const customers = useCustomersStore()
const catalog = useCatalogStore()
const orders = useOrdersStore()
const status = useStatusStore()
const loading = computed(() => customers.loading || catalog.loading || orders.loading || status.loading)

async function refreshAll() {
  await Promise.all([customers.load(), catalog.load(), orders.load(), status.refresh()])
}

async function onOrderCreated() {
  await Promise.all([orders.load(), status.refresh()])
}

watch(() => [status.status?.runId, status.status?.ordersRevision] as const, ([runId, revision], previous) => {
  if (runId && revision !== undefined && (runId !== previous?.[0] || revision !== previous?.[1])) {
    void orders.load(previous?.[0] && runId !== previous[0] ? 0 : orders.result.page)
  }
  if (previous?.[0] && runId !== previous[0]) void customers.load()
})

onMounted(() => { void refreshAll(); status.connect() })
onBeforeUnmount(() => status.disconnect())
</script>

<template>
  <div class="layout">
    <header>
      <a class="brand" href="/">RED<span>Xela</span></a>
      <span class="header-label">Centro de distribución · Simulador</span>
    </header>
    <main>
      <section class="heading">
        <div>
          <p class="eyebrow">SISTEMAS OPERATIVOS 2</p>
          <h1>Panel de operaciones</h1>
          <p class="muted">Clientes, pedidos y recursos del centro de distribución.</p>
        </div>
        <button :disabled="loading" @click="refreshAll">{{ loading ? 'Consultando…' : 'Actualizar todo' }}</button>
      </section>
      <SimulationControls />
      <StatusDashboard />
      <div class="forms-grid">
        <CustomerForm />
        <OrderForm @created="onOrderCreated" />
      </div>
      <OrderList />
      <footer>Día 3 · Motor concurrente y actualizaciones en vivo · Los datos se conservan mientras el servidor permanezca en ejecución.</footer>
    </main>
  </div>
</template>
