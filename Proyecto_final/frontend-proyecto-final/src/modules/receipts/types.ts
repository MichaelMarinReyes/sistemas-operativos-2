export type ReceiptStatus = 'PENDING' | 'WAITING_SCANNER' | 'SCANNING' | 'WAITING_INVENTORY' | 'WAITING_SPACE' | 'COMPLETED' | 'ERROR'

export interface Receipt {
  id: string
  productId: string
  productName: string
  quantity: number
  status: ReceiptStatus
  placedUnits: number
  pendingUnits: number
  createdAt: string
  scannedAt: string | null
  errorMessage: string | null
}

export interface ReceiptReport {
  running: boolean
  incomingQueueSize: number
  inventoryQueueSize: number
  queueCapacity: number
  maxOutstanding: number
  total: number
  outstanding: number
  completed: number
  failed: number
  waitingSpace: number
  items: Receipt[]
}

export const receiptStatusLabels: Record<ReceiptStatus, string> = {
  PENDING: 'Pendiente de recepción', WAITING_SCANNER: 'Esperando escáner', SCANNING: 'Escaneando ingreso',
  WAITING_INVENTORY: 'Esperando inventario', WAITING_SPACE: 'Esperando espacio', COMPLETED: 'Ubicada', ERROR: 'Error',
}
