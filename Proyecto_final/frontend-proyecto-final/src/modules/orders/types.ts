import type { MerchandiseType } from '../catalog/types'

export type OrderStatus = 'CREATED' | 'WAITING_STOCK' | 'QUEUED' | 'WAITING_RESOURCES' | 'PROCESSING' | 'DEADLOCKED' | 'COMPLETED' | 'ERROR'
export type OrderStage = 'NOT_STARTED' | 'PREPARING' | 'SCANNING' | 'LOADING' | 'COMPLETED'
export interface Order {
  id: string
  customerId: string
  customerName: string
  serviceLevel: number
  lines: { productId: string; productName: string; merchandiseType: MerchandiseType; quantity: number }[]
  totalUnits: number
  status: OrderStatus
  createdAt: string
  stage: OrderStage
  progress: number
  estimatedRemainingMinutes: number | null
  startedAt: string | null
  completedAt: string | null
  errorMessage: string | null
}
export interface CreateOrderRequest {
  customerId: string
  lines: { productId: string; quantity: number }[]
}
export interface OrderPage { items: Order[]; page: number; size: number; total: number }
export const orderStatusLabels: Record<OrderStatus, string> = {
  CREATED: 'Creado', WAITING_STOCK: 'Esperando existencias', QUEUED: 'En cola',
  WAITING_RESOURCES: 'Esperando recursos', PROCESSING: 'En proceso', DEADLOCKED: 'Bloqueado por conflicto',
  COMPLETED: 'Completado', ERROR: 'Error',
}

export const orderStageLabels: Record<OrderStage, string> = {
  NOT_STARTED: 'Sin iniciar', PREPARING: 'Preparación y empaque', SCANNING: 'Escaneo de despacho',
  LOADING: 'Carga de despacho', COMPLETED: 'Finalizado',
}
