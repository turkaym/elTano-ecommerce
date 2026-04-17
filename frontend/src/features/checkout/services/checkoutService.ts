import { getJson, postJson } from '../../../shared/api/httpClient'
import type {
  CreateOrderDraftRequest,
  CreateOrderDraftResponse,
  DraftPaymentStatusResponse,
  StartDraftPaymentResponse,
} from '../../../shared/types/checkout'

export function createOrderDraft(payload: CreateOrderDraftRequest) {
  return postJson<CreateOrderDraftRequest, CreateOrderDraftResponse>('/api/orders/drafts', payload)
}

export function startDraftPayment(draftId: string) {
  return postJson<undefined, StartDraftPaymentResponse>(`/api/orders/drafts/${draftId}/payment-preference`, undefined)
}

export function getDraftPaymentStatus(draftId: string) {
  return getJson<DraftPaymentStatusResponse>(`/api/orders/drafts/${draftId}/payment-status`)
}
