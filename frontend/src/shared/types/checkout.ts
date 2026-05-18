export interface CartItem {
  variantId: string
  productName: string
  unitLabel: string
  price: number
  quantity: number
  stockAvailable: number
  productId?: string
  categoryName?: string
  imageUrl?: string
  imageAltText?: string | null
}

export interface CartTotals {
  itemCount: number
  subtotal: number
  total: number
}

export interface CreateOrderDraftRequestItem {
  variantId: string
  quantity: number
}

export interface CreateOrderDraftRequest {
  customerName: string
  phone: string
  note?: string
  items: CreateOrderDraftRequestItem[]
}

export interface CreateOrderDraftResponse {
  draftId: string
  reference: string
  currency: string
  subtotal: number
  total: number
  whatsappMessage: string
}

export interface StartDraftPaymentResponse {
  draftId: string
  preferenceId: string
  initPoint: string
}

export type DraftPaymentStatus = 'PAYMENT_PENDING' | 'PAID' | 'FAILED' | 'CANCELLED' | 'EXPIRED' | 'DRAFT'

export interface DraftPaymentStatusResponse {
  draftId: string
  reference: string
  status: DraftPaymentStatus
  updatedAt: string
  canRetry: boolean
}
