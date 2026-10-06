<script setup lang="ts">
import { computed, ref } from 'vue'
import { useInventoryStore } from '../store'

/**
 * Mapa del almacén y reparto de los lotes.
 *
 * El mapa dibuja cada ubicación con su ocupación, que es la vista que permite ver el almacén real y
 * no un número agregado. La lista de lotes es la que demuestra el almacenamiento no contiguo: cada
 * lote indica en cuántas ubicaciones está repartido y cuáles son, así que se ve que un lote puede
 * ocupar posiciones separadas por otras.
 */
const store = useInventoryStore()
const selectedLot = ref<string | null>(null)

const report = computed(() => store.warehouse?.warehouse ?? null)

/** Pasillos del almacén, agrupados para poder dibujar cada estante con sus dos niveles. */
const aisles = computed(() => {
  const locations = report.value?.locations ?? []
  const byAisle = new Map<string, typeof locations>()
  for (const location of locations) {
    const aisle = location.id.split('-')[0]
    if (!byAisle.has(aisle)) byAisle.set(aisle, [])
    byAisle.get(aisle)!.push(location)
  }
  return [...byAisle.entries()].map(([aisle, shelfLocations]) => ({
    aisle,
    levels: [...new Set(shelfLocations.map(location => location.id.split('-')[2]))]
      .sort()
      .map(level => ({
        level,
        locations: shelfLocations
          .filter(location => location.id.split('-')[2] === level)
          .sort((first, second) => Number(first.id.split('-')[1]) - Number(second.id.split('-')[1])),
      })),
  }))
})

/** Porcentaje de ocupación de una ubicación, para colorear la casilla. */
function fill(location: { capacity: number; usedVolume: number }) {
  return Math.round((location.usedVolume / location.capacity) * 100)
}

function title(location: { id: string; usedVolume: number; capacity: number }) {
  return `${location.id}: ${location.usedVolume} de ${location.capacity} unidades de volumen`
}

const lots = computed(() => report.value?.lots ?? [])
const selected = computed(() => lots.value.find(lot => lot.id === selectedLot.value) ?? null)
</script>

<template>
  <section class="panel warehouse-panel" aria-labelledby="warehouse-heading">
    <div class="section-heading">
      <h2 id="warehouse-heading">Almacén</h2>
      <button :disabled="store.loading" @click="store.load">
        {{ store.loading ? 'Consultando…' : 'Actualizar' }}
      </button>
    </div>

    <template v-if="report">
      <p>
        {{ report.locationCount }} ubicaciones · {{ report.volumePerLocation }} unidades de volumen por
        ubicación · {{ report.locationsInUse }} en uso
      </p>
      <p class="muted">
        Volumen ocupado {{ report.occupiedVolume }} de
        {{ report.locationCount * report.volumePerLocation }} ({{ report.availableVolume }} libres).
        {{ report.splitLots }} de {{ lots.length }} lotes están repartidos entre varias ubicaciones.
      </p>

      <div class="warehouse-map" role="group" aria-label="Ocupación de cada ubicación del almacén">
        <div v-for="aisle in aisles" :key="aisle.aisle" class="aisle">
          <span class="aisle-label">Pasillo {{ aisle.aisle }}</span>
          <div v-for="level in aisle.levels" :key="level.level" class="shelf">
            <div
              v-for="location in level.locations"
              :key="location.id"
              class="cell"
              :class="{ empty: location.usedVolume === 0 }"
              :style="{ '--fill': `${fill(location)}%` }"
              :title="title(location)"
            >
              <span class="cell-id">{{ location.id }}</span>
            </div>
          </div>
        </div>
      </div>
      <p class="muted legend">
        <span class="swatch" /> Cuanto más oscura la casilla, más volumen ocupado.
      </p>

      <h3>Reparto de lotes</h3>
      <p class="muted">
        Cada lote indica las ubicaciones donde tiene unidades. Un lote repartido no necesita un
        tramo seguido de estantes.
      </p>
      <div class="table-scroll">
        <table>
          <caption class="sr-only">Lotes y ubicaciones donde están repartidos</caption>
          <thead>
            <tr>
              <th scope="col">Lote</th>
              <th scope="col">Producto</th>
              <th scope="col">Unidades</th>
              <th scope="col">Volumen</th>
              <th scope="col">Ubicaciones</th>
              <th scope="col">Recepciones</th>
              <th scope="col">Reparto</th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="lot in lots"
              :key="lot.id"
              :class="{ selected: selectedLot === lot.id }"
            >
              <th scope="row">
                <button class="secondary" :aria-expanded="selectedLot === lot.id"
                  @click="selectedLot = selectedLot === lot.id ? null : lot.id">{{ lot.id }}</button>
              </th>
              <td>{{ lot.productName }}</td>
              <td>{{ lot.units }}</td>
              <td>{{ lot.usedVolume }}</td>
              <td>{{ lot.locationCount }}</td>
              <td>{{ lot.receipts }}</td>
              <td>
                <span class="badge" :class="{ stale: lot.split }">
                  {{ lot.split ? 'Varias ubicaciones' : 'Una ubicación' }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <p v-if="!lots.length" class="muted">No hay lotes en el almacén.</p>
      <div v-if="selected" class="panel lot-detail">
        <h4>{{ selected.id }} · {{ selected.productName }}</h4>
        <p class="muted">
          {{ selected.units }} unidades repartidas en {{ selected.locationCount }}
          {{ selected.locationCount === 1 ? 'ubicación' : 'ubicaciones' }}.
        </p>
        <ul class="lot-locations">
          <li v-for="allocation in selected.allocations" :key="allocation.locationId">
            <strong>{{ allocation.locationId }}</strong> · {{ allocation.units }} unidades ·
            {{ allocation.usedVolume }} de volumen
          </li>
        </ul>
      </div>
    </template>
    <p v-else-if="!store.loading" class="muted">Todavía no se ha consultado el almacén.</p>
  </section>
</template>
