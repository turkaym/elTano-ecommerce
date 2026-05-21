import { describe, expect, it } from 'vitest'
import { adminWriteStateReducer } from './adminWriteState'

describe('adminWriteStateReducer', () => {
  it('transitions idle -> pending -> success', () => {
    const pending = adminWriteStateReducer({ status: 'idle' }, { type: 'pending' })
    const success = adminWriteStateReducer(pending, { type: 'success', message: 'ok' })
    expect(pending.status).toBe('pending')
    expect(success).toMatchObject({ status: 'success', message: 'ok' })
  })

  it('supports retry from error and reset', () => {
    const error = adminWriteStateReducer({ status: 'pending' }, { type: 'error', error: { message: 'fallo' } })
    const retry = adminWriteStateReducer(error, { type: 'pending' })
    const reset = adminWriteStateReducer(retry, { type: 'reset' })
    expect(error.status).toBe('error')
    expect(retry.status).toBe('pending')
    expect(reset.status).toBe('idle')
  })
})
