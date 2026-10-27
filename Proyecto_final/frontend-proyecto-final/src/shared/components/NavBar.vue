<script setup lang="ts">
import { ref } from 'vue'

const emit = defineEmits<{ (e: 'change', view: string): void }>()

const active = ref('recursos')

const items = [
  { id: 'recursos', label: 'Recursos del centro' },
  { id: 'clientes', label: 'Clientes' },
  { id: 'productos', label: 'Productos' },
  { id: 'pedidos', label: 'Pedidos' },
  { id: 'recepcion', label: 'Recepción de mercancía' },
  { id: 'inventario', label: 'Inventario de mercancía' },
  { id: 'almacen', label: 'Almacén' },
  { id: 'resumen-almacen', label: 'Resumen de almacén y existencias' }
]

function select(view: string) {
  active.value = view
  emit('change', view)
}
</script>

<template>
  <nav class="top-nav" aria-label="Navegación principal">
    <ul class="nav-list">
      <li v-for="item in items" :key="item.id" class="nav-item">
        <button
          class="nav-button"
          :class="{ active: active === item.id }"
          :aria-current="active === item.id ? 'page' : undefined"
          @click="select(item.id)"
        >
          {{ item.label }}
        </button>
      </li>
    </ul>
  </nav>
</template>

<style scoped>
.top-nav {
  width: 100%;
  background: linear-gradient(90deg, #0f172a 0%, #1e293b 100%);
  border-bottom: 1px solid rgba(148, 163, 184, 0.4);
  position: sticky;
  top: 0;
  z-index: 50;
  overflow-x: auto;
  overflow-y: hidden;
  scrollbar-width: none;
}

.top-nav::-webkit-scrollbar {
  display: none;
}

.nav-list {
  display: flex;
  align-items: center;
  gap: 6px;
  margin: 0 auto;
  max-width: 1600px;
  padding: 6px 12px;
  list-style: none;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.nav-button {
  appearance: none;
  border: 1px solid transparent;
  background: transparent;
  color: #e2e8f0;
  font-size: 0.98rem;
  padding: 8px 14px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 150ms ease-in-out;
}

.nav-button:hover {
  background: rgba(148, 163, 184, 0.25);
  color: #f8fafc;
}

.nav-button.active {
  background: #2563eb;
  color: #ffffff;
  font-weight: 600;
  box-shadow: 0 4px 12px rgba(37, 99, 235, 0.25);
}
</style>
