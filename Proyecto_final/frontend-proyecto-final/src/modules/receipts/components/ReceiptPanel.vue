<script setup lang="ts">
import { computed, ref } from 'vue'
import { useCatalogStore } from '../../catalog/store'
import { useStatusStore } from '../../status/store'
import { errorMessage } from '../../../shared/api/http'
import { createReceipt } from '../api'
import { receiptStatusLabels } from '../types'

const catalog = useCatalogStore()
const status = useStatusStore()
const report = computed(() => status.status?.receipts)
const productId = ref('GEN-01')
const quantity = ref(1)
const saving = ref(false)
const error = ref('')
const success = ref('')
let attempt: { payload: string; key: string } | null = null

/** Un reintento con los mismos datos conserva la clave, incluso si se perdió la respuesta HTTP. */
async function submit() {
  if (saving.value) return
  error.value = ''
  success.value = ''
  if (!catalog.products.some(product => product.id === productId.value)) {
    error.value = 'Selecciona un producto del catálogo.'; return
  }
  if (!Number.isInteger(quantity.value) || quantity.value < 1 || quantity.value > 1000000) {
    error.value = 'La cantidad debe ser un entero entre uno y un millón.'; return
  }
  const payload = JSON.stringify([productId.value, quantity.value])
  if (attempt?.payload !== payload) attempt = { payload, key: crypto.randomUUID() }
  saving.value = true
  try {
    const receipt = await createReceipt(productId.value, quantity.value, attempt!.key)
    success.value = `Recepción ${receipt.id} aceptada: ${receipt.quantity} unidades de ${receipt.productName}.`
    attempt = null
    await status.refresh()
  } catch (cause) {
    error.value = `${errorMessage(cause)} Si reintentas con los mismos datos se conserva la clave para evitar duplicados.`
  } finally { saving.value = false }
}
</script>

<template>
  <section class="panel receipt-panel" aria-labelledby="receipts-heading">
    <h2 id="receipts-heading">Recepción de mercancía</h2>
    <p class="muted">Recepción comparte el único escáner con despacho. Después del escaneo, un trabajador de inventario recibe el mensaje y coloca la mercancía.</p>
    <p v-if="report && !report.running" class="notice">Recepción está detenida. Puedes registrar ingresos; se procesarán al iniciar la simulación.</p>
    <form novalidate @submit.prevent="submit">
      <fieldset :disabled="saving || catalog.loading">
        <label for="receipt-product">Producto recibido</label>
        <select id="receipt-product" v-model="productId">
          <option v-for="product in catalog.products" :key="product.id" :value="product.id">{{ product.name }} · {{ product.id }}</option>
        </select>
        <label for="receipt-quantity">Cantidad recibida</label>
        <input id="receipt-quantity" v-model.number="quantity" type="number" min="1" max="1000000" step="1" />
        <button type="submit">{{ saving ? 'Registrando…' : 'Registrar recepción' }}</button>
      </fieldset>
    </form>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <p v-if="success" class="notice success" role="status">{{ success }}</p>
    <template v-if="report">
      <p>Registradas: {{ report.total }} · Ubicadas: {{ report.completed }} · Pendientes: {{ report.outstanding }} · Sin espacio: {{ report.waitingSpace }} · Con error: {{ report.failed }}</p>
      <p class="muted">Cola de recepción: {{ report.incomingQueueSize }}/{{ report.queueCapacity }} · Cola de inventario: {{ report.inventoryQueueSize }}/{{ report.queueCapacity }} · Máximo pendiente: {{ report.maxOutstanding }}.</p>
      <p class="muted">Lo que no cabe conserva su mensaje y espera espacio sin retener el escáner. Otras mercancías que sí caben pueden ingresar. Se muestran las últimas 50 recepciones.</p>
      <div class="table-scroll">
        <table>
          <caption class="sr-only">Seguimiento de recepciones</caption>
          <thead><tr><th>Recepción</th><th>Producto</th><th>Unidades</th><th>Ubicadas</th><th>Sin ubicación</th><th>Estado</th></tr></thead>
          <tbody>
            <tr v-for="receipt in report.items" :key="receipt.id">
              <th scope="row"><span class="order-id">{{ receipt.id }}</span></th>
              <td>{{ receipt.productName }}</td><td>{{ receipt.quantity }}</td><td>{{ receipt.placedUnits }}</td><td>{{ receipt.pendingUnits }}</td>
              <td>{{ receiptStatusLabels[receipt.status] }}<small v-if="receipt.errorMessage" class="error">{{ receipt.errorMessage }}</small></td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="!report.items.length" class="muted">Todavía no hay recepciones registradas.</p>
    </template>
  </section>
</template>
