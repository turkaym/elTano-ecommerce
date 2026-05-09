import { useReducer } from 'react'
import type { AdminWriteError } from '../services/adminOperationsService'

export type AdminWriteStatus = 'idle' | 'pending' | 'success' | 'error'

export interface AdminWriteFeedback extends AdminWriteError {
  status: AdminWriteStatus
  message?: string
  code?: string
  correlationId?: string
  fieldErrors?: { field: string; message: string }[]
}

type Action =
  | { type: 'pending' }
  | { type: 'success'; message?: string }
  | { type: 'error'; error: AdminWriteError }
  | { type: 'reset' }

const initialState: AdminWriteFeedback = { status: 'idle' }

export function adminWriteStateReducer(state: AdminWriteFeedback, action: Action): AdminWriteFeedback {
  switch (action.type) {
    case 'pending':
      return { status: 'pending' }
    case 'success':
      return { status: 'success', message: action.message ?? 'Operación completada.' }
    case 'error':
      return { status: 'error', ...action.error }
    case 'reset':
      return initialState
    default:
      return state
  }
}

export function useAdminWriteState() {
  const [feedback, dispatch] = useReducer(adminWriteStateReducer, initialState)

  return {
    feedback,
    isPending: feedback.status === 'pending',
    start: () => dispatch({ type: 'pending' }),
    fail: (error: AdminWriteError) => dispatch({ type: 'error', error }),
    succeed: (message?: string) => dispatch({ type: 'success', message }),
    reset: () => dispatch({ type: 'reset' }),
  }
}
