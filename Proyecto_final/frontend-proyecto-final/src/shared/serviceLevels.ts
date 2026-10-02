/** Etiquetas en español; los identificadores coinciden con el contrato del backend. */
export const serviceLevels = [
  { value: 1, label: 'Exprés', target: 'Atención casi inmediata' },
  { value: 2, label: 'Prioritario', target: 'Máximo 10 minutos en espera' },
  { value: 3, label: 'Estándar', target: 'Máximo 30 minutos en espera' },
  { value: 4, label: 'Programado', target: 'Máximo 60 minutos en espera' },
  { value: 5, label: 'Económico', target: 'Máximo 120 minutos en espera' },
]

export function serviceLevelLabel(level: number): string {
  return serviceLevels.find(item => item.value === level)?.label || 'Sin nivel asignado'
}
