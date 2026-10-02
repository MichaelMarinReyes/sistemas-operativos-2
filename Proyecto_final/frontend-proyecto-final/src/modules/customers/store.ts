import { defineStore } from 'pinia'
import { ref } from 'vue'
import { errorMessage } from '../../shared/api/http'
import { createCustomer, fetchCustomers } from './api'
import type { Customer, CreateCustomerRequest } from './types'

export const useCustomersStore = defineStore('customers', () => {
  const customers = ref<Customer[]>([])
  const selectedCustomerId = ref('')
  const loading = ref(false)
  const error = ref('')
  let requestVersion = 0

  async function load() {
    const version = ++requestVersion
    loading.value = true
    error.value = ''
    try {
      const response = await fetchCustomers()
      if (version !== requestVersion) return
      customers.value = response
      if (!customers.value.some(customer => customer.id === selectedCustomerId.value)) selectedCustomerId.value = ''
    } catch (cause) {
      if (version === requestVersion) error.value = errorMessage(cause)
    } finally { if (version === requestVersion) loading.value = false }
  }

  async function create(input: CreateCustomerRequest) {
    const customer = await createCustomer(input)
    // Una consulta previa no debe ocultar la cuenta recién creada al terminar después.
    await load()
    if (!customers.value.some(item => item.id === customer.id)) customers.value.push(customer)
    selectedCustomerId.value = customer.id
    return customer
  }

  return { customers, selectedCustomerId, loading, error, load, create }
})
