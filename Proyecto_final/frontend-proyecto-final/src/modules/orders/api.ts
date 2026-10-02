import { request } from '../../shared/api/http'
import type { CreateOrderRequest, Order, OrderPage } from './types'

export const fetchOrders = (page: number) => request<OrderPage>(`/orders?page=${page}&size=10`)
export const createOrder = (input: CreateOrderRequest) =>
  request<Order>('/orders', { method: 'POST', body: JSON.stringify(input) })
