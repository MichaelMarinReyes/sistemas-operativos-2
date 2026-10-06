import { request } from '../../shared/api/http'
import type { Receipt } from './types'

export function createReceipt(productId: string, quantity: number, key: string) {
  return request<Receipt>('/receipts', {
    method: 'POST', headers: { 'Idempotency-Key': key }, body: JSON.stringify({ productId, quantity }),
  })
}
