<script setup lang="ts">
import { computed, ref } from 'vue'
import { errorMessage } from '../../../shared/api/http'
import { serviceLevelLabel } from '../../../shared/serviceLevels'
import { useCustomersStore } from '../../customers/store'
import { useCatalogStore } from '../../catalog/store'
import { merchandiseLabels } from '../../catalog/types'
import { createOrder } from '../api'

const emit = defineEmits<{ created: [] }>()
const customers = useCustomersStore()
const catalog = useCatalogStore()
let nextKey = 1
const lines = ref([{ key: nextKey++, productId: '', quantity: 1 }])
const saving = ref(false)
const error = ref('')
const success = ref('')
const selectedCustomer = computed(() => customers.customers.find(customer => customer.id === customers.selectedCustomerId))
const totalUnits = computed(() => lines.value.reduce((total, line) => total + (Number(line.quantity) || 0), 0))
const unavailable = computed(() => customers.loading || catalog.loading || !customers.customers.length || !catalog.products.length)

async function submit() {
  if (saving.value) return
  error.value = ''
  success.value = ''
  if (!selectedCustomer.value) { error.value = 'Selecciona un cliente válido.'; return }
  if (lines.value.some(line => !line.productId || !Number.isInteger(line.quantity) || line.quantity < 1 || line.quantity > 1000000)) {
    error.value = 'Selecciona los productos e indica cantidades enteras entre 1 y 1000000.'; return
  }
  if (new Set(lines.value.map(line => line.productId)).size !== lines.value.length) {
    error.value = 'No repitas productos. Modifica la cantidad de la línea existente.'; return
  }
  saving.value = true
  try {
    const order = await createOrder({ customerId: selectedCustomer.value.id,
      lines: lines.value.map(({ productId, quantity }) => ({ productId, quantity })) })
    success.value = `Pedido ${order.id} registrado con ${order.totalUnits} unidades.`
    lines.value = [{ key: nextKey++, productId: '', quantity: 1 }]
    emit('created')
  } catch (cause) { error.value = errorMessage(cause) }
  finally { saving.value = false }
}
</script>

<template>
  <section class="panel" aria-labelledby="order-heading">
    <h2 id="order-heading">Registrar pedido</h2>
    <p class="muted">Agrega uno o varios productos del catálogo.</p>
    <p v-if="customers.error || catalog.error" class="notice error" role="alert">{{ customers.error || catalog.error }}</p>
    <p v-if="!customers.loading && !customers.customers.length" class="notice">Crea una cuenta para registrar su primer pedido.</p>
    <form novalidate @submit.prevent="submit">
      <fieldset :disabled="saving || unavailable">
        <label for="order-customer">Cliente</label>
        <select id="order-customer" v-model="customers.selectedCustomerId" required>
          <option value="" disabled>Selecciona un cliente</option>
          <option v-for="customer in customers.customers" :key="customer.id" :value="customer.id">
            {{ customer.name }} · {{ serviceLevelLabel(customer.serviceLevel) }} · {{ customer.id.slice(0, 8) }}
          </option>
        </select>
        <p v-if="selectedCustomer" class="muted">Servicio contratado: {{ serviceLevelLabel(selectedCustomer.serviceLevel) }}.</p>
        <div v-for="(line, index) in lines" :key="line.key" class="order-line">
          <div>
            <label :for="`product-${line.key}`">Producto {{ index + 1 }}</label>
            <select :id="`product-${line.key}`" v-model="line.productId" required>
              <option value="" disabled>Selecciona un producto</option>
              <option v-for="product in catalog.products" :key="product.id" :value="product.id"
                :disabled="lines.some(other => other.key !== line.key && other.productId === product.id)">
                {{ product.name }} · {{ merchandiseLabels[product.merchandiseType] }}
              </option>
            </select>
          </div>
          <div>
            <label :for="`quantity-${line.key}`">Unidades</label>
            <input :id="`quantity-${line.key}`" v-model.number="line.quantity" type="number" required min="1" max="1000000" step="1" />
          </div>
          <button type="button" class="secondary remove-line" :disabled="lines.length === 1"
            :aria-label="`Quitar producto ${index + 1}`" @click="lines.splice(index, 1)">Quitar</button>
        </div>
        <button type="button" class="secondary" :disabled="lines.length >= catalog.products.length"
          @click="lines.push({ key: nextKey++, productId: '', quantity: 1 })">Agregar producto</button>
        <p><strong>Total: {{ totalUnits }} unidades</strong></p>
        <button type="submit">{{ saving ? 'Registrando…' : 'Registrar pedido' }}</button>
      </fieldset>
    </form>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <p v-if="success" class="notice success" role="status">{{ success }}</p>
  </section>
</template>
