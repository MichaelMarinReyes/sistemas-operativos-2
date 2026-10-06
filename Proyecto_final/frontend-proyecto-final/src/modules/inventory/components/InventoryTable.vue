<script setup lang="ts">
import { computed } from 'vue'
import { useInventoryStore } from '../store'
import { merchandiseTypeLabels } from '../types'

/**
 * Existencias por producto.
 *
 * Se muestran las cuatro cifras que explican por qué un pedido avanza o espera: lo que hay, lo que
 * está comprometido con pedidos en curso, lo que queda libre y lo que está en el almacén pero aún
 * sin ubicación. La última columna indica cuántas ubicaciones ocupa el producto; el detalle del
 * lote permite comprobar dónde están, sin suponer que sean consecutivas.
 */
const store = useInventoryStore()
const totals = computed(() => store.inventory?.totals ?? null)
</script>

<template>
  <div v-if="store.error" class="notice error" role="alert">{{ store.error }}</div>
  <section class="panel inventory-panel" aria-labelledby="inventory-heading">
    <div class="section-heading">
      <h2 id="inventory-heading">Inventario de mercancía</h2>
      <button :disabled="store.loading" @click="store.load">
        {{ store.loading ? 'Consultando…' : 'Actualizar' }}
      </button>
    </div>

    <template v-if="store.inventory && totals">
      <div class="stats" aria-label="Totales de existencias">
        <article><span>Existencias</span><strong>{{ totals.stock }}</strong></article>
        <article><span>Comprometidas</span><strong>{{ totals.reserved }}</strong></article>
        <article><span>Libres</span><strong>{{ totals.available }}</strong></article>
        <article :class="{ attention: totals.pendingPlacement > 0 }">
          <span>Sin ubicación</span><strong>{{ totals.pendingPlacement }}</strong>
        </article>
      </div>
      <p v-if="totals.pendingPlacement > 0" class="muted">
        Hay mercancía en el almacén que todavía no encontró una ubicación donde caber entera. Se
        coloca en cuanto un pedido libera espacio; nunca se descarta. Solo las unidades ubicadas pueden reservarse.
      </p>

      <div class="table-scroll">
        <table>
          <caption class="sr-only">Existencias por tipo de mercancía</caption>
          <thead>
            <tr>
              <th scope="col">Producto</th>
              <th scope="col">Tipo</th>
              <th scope="col">Volumen unitario</th>
              <th scope="col">Existencias</th>
              <th scope="col">Comprometidas</th>
              <th scope="col">Libres</th>
              <th scope="col">Sin ubicación</th>
              <th scope="col">Ubicaciones</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in store.inventory.items" :key="item.productId">
              <th scope="row">
                {{ item.productName }}
                <small class="muted">{{ item.productId }}</small>
              </th>
              <td>{{ merchandiseTypeLabels[item.merchandiseType] }}</td>
              <td>{{ item.unitVolume }}</td>
              <td>{{ item.stock }}</td>
              <td>{{ item.reserved }}</td>
              <td :class="{ scarce: item.available === 0 }">{{ item.available }}</td>
              <td :class="{ attention: item.pendingPlacement > 0 }">{{ item.pendingPlacement }}</td>
              <td>{{ item.locations }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </template>
    <p v-else-if="!store.loading" class="muted">Todavía no se ha consultado el inventario.</p>
  </section>
</template>
