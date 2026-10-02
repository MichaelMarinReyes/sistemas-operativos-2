export const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

interface ApiProblem {
  detail?: string
  errors?: Record<string, string>
}

export class ApiError extends Error {
  constructor(message: string, public readonly status: number, public readonly errors: Record<string, string> = {}) {
    super(message)
  }
}

/** Centraliza la comunicación y traduce fallas de red a mensajes para el usuario. */
export async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(`${apiBaseUrl}${path}`, {
      ...options,
      headers: { Accept: 'application/json', ...(options.body ? { 'Content-Type': 'application/json' } : {}), ...options.headers },
      signal: AbortSignal.timeout(8000),
    })
  } catch {
    throw new ApiError(options.method === 'POST'
      ? 'No se pudo confirmar la operación. Actualiza el estado antes de volver a enviarla y verifica la conexión.'
      : 'No se pudo conectar con el servidor. Verifica la conexión e intenta nuevamente.', 0)
  }
  if (!response.ok) {
    const problem: ApiProblem = await response.json().catch(() => ({}))
    throw new ApiError(problem.detail || 'No se pudo completar la solicitud.', response.status, problem.errors)
  }
  return response.json() as Promise<T>
}

export function errorMessage(cause: unknown): string {
  if (cause instanceof ApiError) {
    const details = [...new Set(Object.values(cause.errors))]
    return [cause.message, ...details].join(' ')
  }
  return 'Ocurrió un error inesperado. Intenta nuevamente.'
}
