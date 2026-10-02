export type MerchandiseType = 'GENERAL' | 'FRAGILE' | 'HEAVY' | 'BULKY' | 'ELECTRONICS'
export interface Product {
  id: string
  name: string
  merchandiseType: MerchandiseType
  unitVolume: number
}

export const merchandiseLabels: Record<MerchandiseType, string> = {
  GENERAL: 'General', FRAGILE: 'Frágil', HEAVY: 'Pesada', BULKY: 'Voluminosa', ELECTRONICS: 'Electrónica',
}
