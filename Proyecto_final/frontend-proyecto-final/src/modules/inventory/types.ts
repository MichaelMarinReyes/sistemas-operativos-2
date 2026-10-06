import type { MerchandiseType } from '../catalog/types'

/**
 * Forma de las consultas de inventario y almacén.
 *
 * Los nombres coinciden con los registros del backend: cada campo es un dato ya calculado que la
 * vista puede representar sin volver a deducir nada.
 */
export interface InventoryItem {
  productId: string
  productName: string
  merchandiseType: MerchandiseType
  unitVolume: number
  stock: number
  reserved: number
  available: number
  pendingPlacement: number
  locations: number
}

export interface InventoryTotals {
  stock: number
  reserved: number
  available: number
  pendingPlacement: number
  occupiedVolume: number
  availableVolume: number
}

export interface InventoryResponse {
  inventoryRevision: number
  generatedAt: string
  items: InventoryItem[]
  totals: InventoryTotals
}

export interface StorageLocationView {
  id: string
  capacity: number
  usedVolume: number
}

export interface LotAllocationView {
  locationId: string
  units: number
  usedVolume: number
}

export interface LotView {
  id: string
  productId: string
  productName: string
  units: number
  locationCount: number
  /** Indica que el lote ocupa varias ubicaciones, sin afirmar si son consecutivas. */
  split: boolean
  usedVolume: number
  receipts: number
  allocations: LotAllocationView[]
}

export interface WarehouseReport {
  locationCount: number
  volumePerLocation: number
  occupiedVolume: number
  availableVolume: number
  locationsInUse: number
  splitLots: number
  locations: StorageLocationView[]
  lots: LotView[]
}

export interface WarehouseResponse {
  inventoryRevision: number
  generatedAt: string
  warehouse: WarehouseReport
}

export const merchandiseTypeLabels: Record<MerchandiseType, string> = {
  GENERAL: 'General', FRAGILE: 'Frágil', HEAVY: 'Pesado', BULKY: 'Voluminoso', ELECTRONICS: 'Electrónica',
}
