import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { CheckoutForm } from './CheckoutForm'

describe('CheckoutForm', () => {
  it('blocks submit and shows inline validation when required fields are missing', async () => {
    const user = userEvent.setup()
    const onSubmitDraft = vi.fn().mockResolvedValue(undefined)

    render(<CheckoutForm isCartEmpty={false} submitError={null} onSubmitDraft={onSubmitDraft} />)

    await user.click(screen.getByRole('button', { name: 'Crear pedido y confirmar por WhatsApp' }))

    expect(onSubmitDraft).not.toHaveBeenCalled()
    expect(screen.getByText('Ingresa tu nombre y apellido.')).toBeInTheDocument()
    expect(screen.getByText('Ingresa un telefono de contacto.')).toBeInTheDocument()
  })

  it('submits when required fields are valid and cart has items', async () => {
    const user = userEvent.setup()
    const onSubmitDraft = vi.fn().mockResolvedValue(undefined)

    render(<CheckoutForm isCartEmpty={false} submitError={null} onSubmitDraft={onSubmitDraft} />)

    await user.type(screen.getByLabelText('Nombre y apellido *'), 'Marina Diaz')
    await user.type(screen.getByLabelText('Telefono *'), '+5491112345678')
    await user.type(screen.getByLabelText('Nota (opcional)'), 'Sin sal')
    await user.click(screen.getByRole('button', { name: 'Crear pedido y confirmar por WhatsApp' }))

    expect(onSubmitDraft).toHaveBeenCalledTimes(1)
    expect(onSubmitDraft).toHaveBeenCalledWith({
      customerName: 'Marina Diaz',
      phone: '+5491112345678',
      note: 'Sin sal',
    })
  })

  it('omits shopper-facing unavailable checkout warnings while preserving blocked submission', () => {
    const onSubmitDraft = vi.fn().mockResolvedValue(undefined)

    render(
      <CheckoutForm
        isCartEmpty={false}
        isSubmitBlocked
        blockedSubmitMessage="No podemos finalizar el pedido con productos de muestra."
        submitError={null}
        onSubmitDraft={onSubmitDraft}
      />,
    )

    expect(screen.queryByText('No podemos finalizar el pedido con productos de muestra.')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Crear pedido y confirmar por WhatsApp' })).toBeDisabled()
    expect(onSubmitDraft).not.toHaveBeenCalled()
  })
})
