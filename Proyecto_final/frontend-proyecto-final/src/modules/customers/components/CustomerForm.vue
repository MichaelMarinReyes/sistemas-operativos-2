<script setup lang="ts">
import { ref } from 'vue'
import { errorMessage } from '../../../shared/api/http'
import { serviceLevels } from '../../../shared/serviceLevels'
import { useCustomersStore } from '../store'

const store = useCustomersStore()
const name = ref('')
const serviceLevel = ref(3)
const saving = ref(false)
const error = ref('')
const success = ref('')

async function submit() {
  if (saving.value) return
  error.value = ''
  success.value = ''
  if (!name.value.trim()) { error.value = 'El nombre del cliente es obligatorio.'; return }
  if (name.value.trim().length > 100) { error.value = 'El nombre debe tener como máximo 100 caracteres.'; return }
  if (!Number.isInteger(serviceLevel.value) || serviceLevel.value < 1 || serviceLevel.value > 5) {
    error.value = 'Selecciona uno de los cinco niveles de servicio.'; return
  }
  saving.value = true
  try {
    const customer = await store.create({ name: name.value.trim(), serviceLevel: serviceLevel.value })
    success.value = `Cuenta de ${customer.name} creada. Ya puedes registrar su pedido.`
    name.value = ''
  } catch (cause) { error.value = errorMessage(cause) }
  finally { saving.value = false }
}
</script>

<template>
  <section class="panel" aria-labelledby="customer-heading">
    <h2 id="customer-heading">Crear cuenta de cliente</h2>
    <p class="muted">Selecciona el servicio que se aplicará a sus pedidos.</p>
    <form novalidate @submit.prevent="submit">
      <fieldset :disabled="saving">
        <label for="customer-name">Nombre del cliente</label>
        <input id="customer-name" v-model="name" required maxlength="100" autocomplete="name" placeholder="Nombre completo" />
        <label for="service-level">Nivel de servicio</label>
        <select id="service-level" v-model.number="serviceLevel" required>
          <option v-for="level in serviceLevels" :key="level.value" :value="level.value">
            {{ level.value }} · {{ level.label }} — {{ level.target }}
          </option>
        </select>
        <button type="submit">{{ saving ? 'Creando cuenta…' : 'Crear cuenta' }}</button>
      </fieldset>
    </form>
    <p v-if="error" class="notice error" role="alert">{{ error }}</p>
    <p v-if="success" class="notice success" role="status">{{ success }}</p>
  </section>
</template>
