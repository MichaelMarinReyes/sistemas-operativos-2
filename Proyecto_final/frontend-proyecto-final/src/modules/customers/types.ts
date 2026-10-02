export interface Customer {
  id: string
  name: string
  serviceLevel: number
  createdAt: string
}

export interface CreateCustomerRequest {
  name: string
  serviceLevel: number
}
