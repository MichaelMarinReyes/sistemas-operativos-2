<script setup lang="ts">
import { onMounted } from 'vue'
import { useCatalogStore } from '../store'
import { merchandiseLabels } from '../types'

const store = useCatalogStore()

function getVolume(product: any): number {
  if (product.volumeM3 !== undefined && product.volumeM3 !== null) {
    return Number(product.volumeM3)
  }
  const length = Number(product.lengthCm) || 0
  const width = Number(product.widthCm) || 0
  const height = Number(product.heightCm) || 0
  return (length * width * height) / 1000000
}

onMounted(() => {
  if (!store.products.length) {
    store.load()
  }
})
</script>

<template>
  <section class="panel catalog-panel" aria-labelledby="catalog-heading">
    <div class="section-heading">
      <h2 id="catalog-heading">Catálogo de productos</h2>
      <button class="secondary" :disabled="store.loading" @click="store.load()">
        {{ store.loading ? 'Actualizando...' : 'Actualizar catálogo' }}
      </button>
    </div>
    <p class="muted">Productos disponibles para pedidos en el centro de distribución.</p>
    <p v-if="store.error" class="notice error" role="alert">{{ store.error }}</p>
    <p v-if="store.loading" role="status">Cargando catálogo...</p>
    <div class="table-scroll" v-if="!store.loading && store.products.length">
      <table>
        <thead>
          <tr>
            <th>Código</th>
            <th>Producto</th>
            <th>Tipo de mercancía</th>
            <th>Peso (kg)</th>
            <th>Largo x Ancho x Alto (cm)</th>
            <th>Volumen (m³)</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="product in store.products" :key="product.id">
            <td>{{ product.id }}</td>
            <td>{{ product.name }}</td>
            <td>{{ merchandiseLabels[product.merchandiseType] }}</td>
            <td>{{ product.weightKg }}</td>
            <td>{{ product.lengthCm }} x {{ product.widthCm }} x {{ product.heightCm }}</td>
            <td>{{ getVolume(product).toFixed(3) }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="!store.loading && !store.products.length && !store.error" class="muted">
      No hay productos registrados en el catálogo.
    </p>
  </section>
</template>
