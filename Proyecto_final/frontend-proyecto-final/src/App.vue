<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, watch } from 'vue'
import SimulationControls from './modules/simulation/components/SimulationControls.vue'
import CustomerForm from './modules/customers/components/CustomerForm.vue'
import OrderForm from './modules/orders/components/OrderForm.vue'
import OrderList from './modules/orders/components/OrderList.vue'
import StatusDashboard from './modules/status/components/StatusDashboard.vue'
import InventoryTable from './modules/inventory/components/InventoryTable.vue'
import WarehouseMap from './modules/inventory/components/WarehouseMap.vue'
import ReceiptPanel from './modules/receipts/components/ReceiptPanel.vue'
import { useCustomersStore } from './modules/customers/store'
import { useCatalogStore } from './modules/catalog/store'
import { useInventoryStore } from './modules/inventory/store'
import { useOrdersStore } from './modules/orders/store'
import { useStatusStore } from './modules/status/store'

const customers = useCustomersStore()
const catalog = useCatalogStore()
const inventory = useInventoryStore()
const orders = useOrdersStore()
const status = useStatusStore()
const loading = computed(() => customers.loading || catalog.loading || orders.loading
  || status.loading || inventory.loading)

async function refreshAll() {
  await Promise.all([customers.load(), catalog.load(), orders.load(), status.refresh(), inventory.load()])
}

async function onOrderCreated() {
  await Promise.all([orders.load(), status.refresh(), inventory.load()])
}

/**
 * El detalle del inventario solo se vuelve a consultar cuando su revisión cambia.
 *
 * La instantánea del canal en vivo trae la revisión del inventario, así que compararla evita
 * descargar el detalle completo cada 300 milisegundos sin perder nada: si no cambió, lo que ya se
 * tiene sigue siendo exacto.
 */
watch(() => [status.status?.runId, status.status?.inventory.inventoryRevision] as const, ([runId, revision], previous) => {
  if (runId && revision !== undefined && (runId !== previous?.[0] || revision !== previous?.[1])) {
    void inventory.load()
  }
})

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
      <ReceiptPanel />
      <InventoryTable />
      <WarehouseMap />
      <footer>Día 5 · Recepción coordinada, inventario y despacho · Los datos se conservan mientras el servidor permanezca en ejecución.</footer>
    </main>
  </div>
</template>
