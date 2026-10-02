import { request } from '../../shared/api/http'
import type { Customer, CreateCustomerRequest } from './types'

export const fetchCustomers = () => request<Customer[]>('/customers')
export const createCustomer = (input: CreateCustomerRequest) =>
  request<Customer>('/customers', { method: 'POST', body: JSON.stringify(input) })
