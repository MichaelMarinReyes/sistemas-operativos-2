<script setup lang="ts">
import { computed, onMounted, onBeforeUnmount, watch, ref } from 'vue'
import NavBar from './shared/components/NavBar.vue'
import SimulationControls from './modules/simulation/components/SimulationControls.vue'
import CustomerForm from './modules/customers/components/CustomerForm.vue'
import OrderForm from './modules/orders/components/OrderForm.vue'
import OrderList from './modules/orders/components/OrderList.vue'
import StatusDashboard from './modules/status/components/StatusDashboard.vue'
import InventoryTable from './modules/inventory/components/InventoryTable.vue'
import WarehouseMap from './modules/inventory/components/WarehouseMap.vue'
import ReceiptPanel from './modules/receipts/components/ReceiptPanel.vue'
import ProductCatalog from './modules/catalog/components/ProductCatalog.vue'
import { useCustomersStore } from './modules/customers/store'
import { useCatalogStore } from './modules/catalog/store'
import { useInventoryStore } from './modules/inventory/store'
import { useOrdersStore } from './modules/orders/store'
import { useStatusStore } from './modules/status/store'

const currentView = ref('recursos')

const customers = useCustomersStore()
const catalog = useCatalogStore()
const inventory = useInventoryStore()
const orders = useOrdersStore()
const status = useStatusStore()
const loading = computed(() => customers.loading || catalog.loading || orders.loading || status.loading || inventory.loading)

function changeView(view: string) {
  currentView.value = view
}

async function refreshAll() {
  await Promise.all([customers.load(), catalog.load(), orders.load(), status.refresh(), inventory.load()])
}

async function onOrderCreated() {
  await Promise.all([orders.load(), status.refresh(), inventory.load()])
}

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
  <div class="app-shell">
    <header class="app-header">
      <div class="header-top">
        <a class="brand" href="/">RED<span>Xela</span></a>
        <button class="refresh-btn" :disabled="loading" @click="refreshAll">
          {{ loading ? 'Consultando…' : 'Actualizar todo' }}
        </button>
      </div>
      <NavBar @change="changeView" />
    </header>

    <main class="app-content">
      <section v-if="currentView === 'recursos'" class="view-section">
        <div class="section-heading">
          <div>
            <h1>Recursos del centro</h1>
            <p class="muted">Estado general, recursos y solicitudes</p>
          </div>
        </div>
        <SimulationControls />
        <StatusDashboard />
      </section>

      <section v-if="currentView === 'clientes'" class="view-section">
        <div class="section-heading">
          <div>
            <h1>Clientes</h1>
            <p class="muted">Crear cuenta de cliente y registrar pedido juntos</p>
          </div>
        </div>
        <div class="forms-grid">
          <CustomerForm />
          <OrderForm @created="onOrderCreated" />
        </div>
      </section>

      <section v-if="currentView === 'productos'" class="view-section">
        <ProductCatalog />
      </section>

      <section v-if="currentView === 'pedidos'" class="view-section">
        <OrderList />
      </section>

      <section v-if="currentView === 'recepcion'" class="view-section">
        <ReceiptPanel />
      </section>

      <section v-if="currentView === 'inventario'" class="view-section">
        <InventoryTable />
      </section>

      <section v-if="currentView === 'almacen'" class="view-section">
        <WarehouseMap />
      </section>

      <section v-if="currentView === 'resumen-almacen'" class="view-section split-view">
        <div class="split-panel">
          <InventoryTable />
        </div>
        <div class="split-panel">
          <WarehouseMap />
        </div>
      </section>
    </main>
  </div>
</template>

<style>
html,
body {
  height: 100dvh;
  overflow: hidden;
  margin: 0;
}

#app {
  height: 100dvh;
  overflow: hidden;
}
</style>

<style scoped>
.app-shell {
  height: 100dvh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #f8fafc;
}

.app-header {
  display: flex;
  flex-direction: column;
  border-bottom: 1px solid #cbd5e1;
  background: #ffffff;
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 16px 6px;
  max-width: 1600px;
  margin: 0 auto;
  width: 100%;
}

.brand {
  font-weight: 700;
  font-size: 1.4rem;
  color: #0f172a;
  text-decoration: none;
}

.brand span {
  color: #2563eb;
}

.refresh-btn {
  padding: 6px 12px;
  border-radius: 8px;
  border: 1px solid #cbd5e1;
  background: #ffffff;
  cursor: pointer;
}

.app-content {
  flex: 1 1 auto;
  overflow: hidden;
  padding: 12px 16px;
  max-width: 1600px;
  margin: 0 auto;
  width: 100%;
}

.view-section {
  height: 100%;
  overflow: auto;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 4px 4px 12px;
}

.split-view {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
  gap: 16px;
}

.split-panel {
  height: 100%;
  overflow: auto;
  display: flex;
  flex-direction: column;
}

.forms-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 16px;
  align-items: start;
}

@media (max-width: 1080px) {
  .split-view {
    grid-template-columns: 1fr;
  }

  .split-panel {
    height: auto;
    overflow: visible;
  }

  .view-section {
    overflow: auto;
  }
}
</style>
