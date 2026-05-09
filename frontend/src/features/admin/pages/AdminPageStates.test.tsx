import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import { AdminWriteStateBanner } from './AdminPageStates'

describe('AdminWriteStateBanner', () => {
  it('renders pending then success states', () => {
    const { rerender } = render(<AdminWriteStateBanner feedback={{ status: 'pending' }} />)
    expect(screen.getByRole('status')).toHaveTextContent('Guardando cambios')

    rerender(<AdminWriteStateBanner feedback={{ status: 'success', message: 'ok' }} />)
    expect(screen.getByRole('status')).toHaveTextContent('ok')
  })

  it('shows error details and supports dismiss', () => {
    const onDismiss = vi.fn()
    render(
      <AdminWriteStateBanner
        feedback={{ status: 'error', message: 'Falló', correlationId: 'corr-1' }}
        onDismiss={onDismiss}
      />,
    )

    expect(screen.getByRole('alert')).toHaveTextContent('Falló')
    expect(screen.getByText(/corr-1/i)).toBeInTheDocument()
    fireEvent.click(screen.getByRole('button', { name: /Descartar/i }))
    expect(onDismiss).toHaveBeenCalledTimes(1)
  })
})
