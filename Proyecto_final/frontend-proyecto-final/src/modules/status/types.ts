import type { Order } from '../orders/types'
import type { ReceiptReport } from '../receipts/types'

export type SimulationStatus = 'NOT_STARTED' | 'RUNNING' | 'STOPPING' | 'STOPPED'
export type ResourceType = 'WORKER' | 'FORKLIFT' | 'PACKING' | 'QUALITY' | 'SCANNER' | 'LOADING'

export interface StatusResponse {
  contractVersion: number
  runId: string
  sequence: number
  generatedAt: string
  simulationStatus: SimulationStatus
  policy: 'STRICT_PRIORITY'
  elapsedMinutes: number
  timeScale: number
  workerCount: number
  resources: {
    type: ResourceType; name: string; capacity: number; inUse: number
    instances: { id: string; ownerId: string | null; acquiredAt: string | null }[]
  }[]
  resourceRequests: { operationId: string; required: Partial<Record<ResourceType, number>> }[]
  receipts: ReceiptReport
  warehouse: { locations: number; volumePerLocation: number; occupiedVolume: number; availableVolume: number }
  /**
   * Resumen de existencias que viaja en cada instantánea. La revisión permite detectar que el
   * inventario cambió entre eventos, que es lo que dispara la consulta del detalle.
   */
  inventory: {
    inventoryRevision: number; stock: number; reserved: number; pendingPlacement: number; available: number
  }
  orders: { waiting: number; processing: number; completed: number; failed: number }
  ordersRevision: number
  activeOrders: Order[]
  waitingOrders: Order[]
}

export const simulationStatusLabels: Record<SimulationStatus, string> = {
  NOT_STARTED: 'Simulación no iniciada', RUNNING: 'Simulación en ejecución', STOPPING: 'Liberando recursos', STOPPED: 'Simulación detenida',
}

export const resourceLabels: Record<ResourceType, string> = {
  WORKER: 'Encargados', FORKLIFT: 'Montacargas', PACKING: 'Empaque', QUALITY: 'Calidad', SCANNER: 'Escáner', LOADING: 'Carga',
}
