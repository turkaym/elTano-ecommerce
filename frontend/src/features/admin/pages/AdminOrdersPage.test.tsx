import { fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminOrdersPage } from './AdminOrdersPage'
import { ApiClientError } from '../../../shared/api/httpClient'
import {
  getAdminOrderDetail,
  listAdminOrders,
  updateAdminOrderPaymentStatus,
  updateAdminOrderStatus,
  type AdminOrderDetailResponse,
} from '../services/adminOperationsService'

vi.mock('../services/adminOperationsService', () => ({
  listAdminOrders: vi.fn(async () => ({
    items: [
      {
        id: 'ord-1',
        reference: 'ET-2026-0007',
        status: 'PAID',
        customer: 'Ana Gómez',
        paymentStatus: 'approved',
        total: 9500,
        createdAt: '2026-05-01T09:00:00Z',
      },
    ],
    page: 0,
    size: 20,
    totalElements: 1,
    totalPages: 1,
  })),
  getAdminOrderDetail: vi.fn(async () => ({
    id: 'ord-1',
    reference: 'ET-2026-0007',
    status: 'PAID',
    customer: 'Ana Gómez',
    phone: '11223344',
    note: 'Tocar timbre',
    currency: 'ARS',
    subtotal: 9000,
    total: 9500,
    payment: { provider: 'mercadopago', statusDetail: 'approved', externalId: 'pay-1', updatedAt: '2026-05-01T10:00:00Z' },
    items: [{ id: 'line-1', variantId: 'var-1', productName: 'Nuez', unitLabel: '250g', unitPrice: 4500, quantity: 2, subtotal: 9000 }],
    createdAt: '2026-05-01T09:00:00Z',
    updatedAt: '2026-05-01T10:00:00Z',
  })),
  updateAdminOrderStatus: vi.fn(async () => ({
    id: 'ord-1',
    reference: 'ET-2026-0007',
    status: 'CANCELLED',
    customer: 'Ana Gómez',
    phone: '11223344',
    note: 'Tocar timbre',
    currency: 'ARS',
    subtotal: 9000,
    total: 9500,
    payment: { provider: 'mercadopago', statusDetail: 'cancelled', externalId: 'pay-1', updatedAt: '2026-05-01T10:00:00Z' },
    items: [{ id: 'line-1', variantId: 'var-1', productName: 'Nuez', unitLabel: '250g', unitPrice: 4500, quantity: 2, subtotal: 9000 }],
    createdAt: '2026-05-01T09:00:00Z',
    updatedAt: '2026-05-01T10:05:00Z',
  })),
  updateAdminOrderPaymentStatus: vi.fn(async () => ({
    id: 'ord-1',
    reference: 'ET-2026-0007',
    status: 'PAID',
    customer: 'Ana Gómez',
    phone: '11223344',
    note: 'Tocar timbre',
    currency: 'ARS',
    subtotal: 9000,
    total: 9500,
    payment: { provider: 'manual', statusDetail: 'manual_paid', externalId: null, updatedAt: '2026-05-01T10:05:00Z' },
    items: [{ id: 'line-1', variantId: 'var-1', productName: 'Nuez', unitLabel: '250g', unitPrice: 4500, quantity: 2, subtotal: 9000 }],
    createdAt: '2026-05-01T09:00:00Z',
    updatedAt: '2026-05-01T10:05:00Z',
  })),
  mapAdminWriteError: vi.fn((error: unknown) => {
    if (error instanceof ApiClientError) {
      return {
        message: error.message,
        code: error.code,
        correlationId: error.correlationId,
        fieldErrors: error.fieldErrors,
      }
    }
    return { message: 'No se pudo completar la operación.' }
  }),
}))

function orderDetail(overrides: Partial<AdminOrderDetailResponse> = {}): AdminOrderDetailResponse {
  return {
    id: 'ord-1',
    reference: 'ET-2026-0007',
    status: 'PAID',
    customer: 'Ana Gómez',
    phone: '11223344',
    note: 'Tocar timbre',
    currency: 'ARS',
    subtotal: 9000,
    total: 9500,
    payment: { provider: 'mercadopago', statusDetail: 'approved', externalId: 'pay-1', updatedAt: '2026-05-01T10:00:00Z' },
    items: [{ id: 'line-1', variantId: 'var-1', productName: 'Nuez', unitLabel: '250g', unitPrice: 4500, quantity: 2, subtotal: 9000 }],
    createdAt: '2026-05-01T09:00:00Z',
    updatedAt: '2026-05-01T10:00:00Z',
    ...overrides,
  }
}

describe('AdminOrdersPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.mocked(listAdminOrders).mockImplementation(async () => ({
      items: [
        {
          id: 'ord-1',
          reference: 'ET-2026-0007',
          status: 'PAID',
          customer: 'Ana Gómez',
          paymentStatus: 'approved',
          total: 9500,
          createdAt: '2026-05-01T09:00:00Z',
        },
      ],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
    }))
    vi.spyOn(window, 'confirm').mockReturnValue(true)
  })

  it('renders order filters, paging metadata, and summary columns', async () => {
    render(<AdminOrdersPage />)

    expect(await screen.findByRole('heading', { name: 'Pedidos' })).toBeInTheDocument()
    expect(screen.getByLabelText(/Estado pedido/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/Cliente o referencia/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/Fecha desde/i)).toBeInTheDocument()
    expect(screen.getByLabelText(/Fecha hasta/i)).toBeInTheDocument()
    expect(screen.getByRole('article', { name: /Pedido ET-2026-0007/i })).toHaveTextContent('Ana Gómez')
    expect(screen.getByRole('article', { name: /Pedido ET-2026-0007/i })).toHaveTextContent('approved')
    expect(screen.getByText(/Mostrando 1 de 1 pedidos/i)).toBeInTheDocument()
  })

  it('applies supported filters with paging and keeps list read-only', async () => {
    render(<AdminOrdersPage />)
    await screen.findByText('ET-2026-0007')

    fireEvent.change(screen.getByLabelText(/Estado pedido/i), { target: { value: 'PAID' } })
    fireEvent.change(screen.getByLabelText(/Cliente o referencia/i), { target: { value: 'Ana' } })
    fireEvent.change(screen.getByLabelText(/Fecha desde/i), { target: { value: '2026-05-01' } })
    fireEvent.change(screen.getByLabelText(/Fecha hasta/i), { target: { value: '2026-05-31' } })
    fireEvent.click(screen.getByRole('button', { name: /Filtrar pedidos/i }))

    await waitFor(() =>
      expect(vi.mocked(listAdminOrders)).toHaveBeenLastCalledWith({
        status: 'PAID',
        query: 'Ana',
        from: '2026-05-01',
        to: '2026-05-31',
        page: 0,
        size: 20,
      }),
    )
    expect(screen.queryByRole('button', { name: /Cambiar estado/i })).not.toBeInTheDocument()
  })

  it('uses the single search box as an OR query for customer names', async () => {
    vi.mocked(listAdminOrders).mockImplementation(async (params = {}) => {
      if (params.query?.toLowerCase() === 'farid') {
        return {
          items: [{ id: 'ord-farid', reference: 'ET-2026-A2F8F0', status: 'PAID', customer: 'farid', paymentStatus: 'approved', total: 12000, createdAt: '2026-05-11T14:30:00Z' }],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        }
      }
      return { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
    })

    render(<AdminOrdersPage />)
    await screen.findByRole('heading', { name: /Sin pedidos/i })

    fireEvent.change(screen.getByLabelText(/Cliente o referencia/i), { target: { value: 'farid' } })
    fireEvent.change(screen.getByLabelText(/Fecha desde/i), { target: { value: '2026-05-11' } })
    fireEvent.change(screen.getByLabelText(/Fecha hasta/i), { target: { value: '2026-05-11' } })
    fireEvent.click(screen.getByRole('button', { name: /Filtrar pedidos/i }))

    expect(await screen.findByRole('article', { name: /Pedido ET-2026-A2F8F0/i })).toHaveTextContent('farid')
    expect(vi.mocked(listAdminOrders)).toHaveBeenLastCalledWith({
      query: 'farid',
      from: '2026-05-11',
      to: '2026-05-11',
      page: 0,
      size: 20,
    })
  })

  it('uses the single search box as an OR query for order references', async () => {
    vi.mocked(listAdminOrders).mockImplementation(async (params = {}) => {
      if (params.query === 'ET-2026-A2F8F0') {
        return {
          items: [{ id: 'ord-farid', reference: 'ET-2026-A2F8F0', status: 'PAID', customer: 'farid', paymentStatus: 'approved', total: 12000, createdAt: '2026-05-11T14:30:00Z' }],
          page: 0,
          size: 20,
          totalElements: 1,
          totalPages: 1,
        }
      }
      return { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
    })

    render(<AdminOrdersPage />)
    await screen.findByRole('heading', { name: /Sin pedidos/i })

    fireEvent.change(screen.getByLabelText(/Cliente o referencia/i), { target: { value: 'ET-2026-A2F8F0' } })
    fireEvent.click(screen.getByRole('button', { name: /Filtrar pedidos/i }))

    expect(await screen.findByRole('article', { name: /Pedido ET-2026-A2F8F0/i })).toHaveTextContent('farid')
    expect(vi.mocked(listAdminOrders)).toHaveBeenLastCalledWith({
      query: 'ET-2026-A2F8F0',
      page: 0,
      size: 20,
    })
  })

  it('loads selected order detail with items, customer and payment information', async () => {
    render(<AdminOrdersPage />)
    await screen.findByText('ET-2026-0007')

    fireEvent.click(screen.getByRole('button', { name: /Ver detalle ET-2026-0007/i }))

    const dialog = await screen.findByRole('dialog', { name: /Detalle ET-2026-0007/i })
    const detail = await screen.findByRole('region', { name: /Detalle pedido ET-2026-0007/i })
    expect(dialog).toContainElement(detail)
    expect(within(detail).getByText(/Ana Gómez/)).toBeInTheDocument()
    expect(within(detail).getByText(/11223344/)).toBeInTheDocument()
    expect(within(detail).getByText(/mercadopago/)).toBeInTheDocument()
    expect(within(detail).getByText(/Nuez/)).toBeInTheDocument()
    expect(within(detail).getByText(/2 ×/)).toBeInTheDocument()
    expect(vi.mocked(getAdminOrderDetail)).toHaveBeenCalledWith('ord-1')
  })

  it('opens order detail in a closable dialog and returns focus to the trigger', async () => {
    render(<AdminOrdersPage />)
    await screen.findByText('ET-2026-0007')

    const trigger = screen.getByRole('button', { name: /Ver detalle ET-2026-0007/i })
    fireEvent.click(trigger)

    const dialog = await screen.findByRole('dialog', { name: /Detalle ET-2026-0007/i })
    expect(dialog).toBeInTheDocument()
    expect(dialog).toHaveFocus()

    fireEvent.click(within(dialog).getByRole('button', { name: /Cerrar/i }))

    expect(screen.queryByRole('dialog', { name: /Detalle ET-2026-0007/i })).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
  })

  it('keeps Tab and Shift+Tab focus movement inside the order detail dialog', async () => {
    const user = userEvent.setup()
    render(<AdminOrdersPage />)
    await screen.findByText('ET-2026-0007')

    await user.click(screen.getByRole('button', { name: /Ver detalle ET-2026-0007/i }))

    const dialog = await screen.findByRole('dialog', { name: /Detalle ET-2026-0007/i })
    const closeButton = within(dialog).getByRole('button', { name: /Cerrar/i })
    const markPreparingButton = within(dialog).getByRole('button', { name: /Marcar como preparando/i })

    expect(dialog).toHaveFocus()
    await user.tab({ shift: true })
    expect(markPreparingButton).toHaveFocus()

    markPreparingButton.focus()
    await user.tab()
    expect(closeButton).toHaveFocus()

    closeButton.focus()
    await user.tab({ shift: true })
    expect(markPreparingButton).toHaveFocus()
  })

  it('confirms a supported status transition, updates order status, refreshes data, and shows success', async () => {
    vi.mocked(getAdminOrderDetail).mockResolvedValueOnce({
      id: 'ord-1',
      reference: 'ET-2026-0007',
      status: 'PAYMENT_PENDING',
      customer: 'Ana Gómez',
      phone: '11223344',
      note: 'Tocar timbre',
      currency: 'ARS',
      subtotal: 9000,
      total: 9500,
      payment: { provider: 'mercadopago', statusDetail: 'pending', externalId: 'pay-1', updatedAt: '2026-05-01T10:00:00Z' },
      items: [{ id: 'line-1', variantId: 'var-1', productName: 'Nuez', unitLabel: '250g', unitPrice: 4500, quantity: 2, subtotal: 9000 }],
      createdAt: '2026-05-01T09:00:00Z',
      updatedAt: '2026-05-01T10:00:00Z',
    })

    render(<AdminOrdersPage />)
    await screen.findByText('ET-2026-0007')
    fireEvent.click(screen.getByRole('button', { name: /Ver detalle ET-2026-0007/i }))
    await screen.findByRole('region', { name: /Detalle pedido ET-2026-0007/i })

    fireEvent.click(screen.getByRole('button', { name: /Cancelar pedido/i }))

    await waitFor(() => expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('Cancelado')))
    await waitFor(() => expect(vi.mocked(updateAdminOrderStatus)).toHaveBeenCalledWith('ord-1', 'CANCELLED'))
    expect(await screen.findByText(/Estado del pedido actualizado a CANCELLED/i)).toBeInTheDocument()
    expect(vi.mocked(listAdminOrders)).toHaveBeenCalledTimes(2)
    expect(vi.mocked(getAdminOrderDetail)).toHaveBeenLastCalledWith('ord-1')
  })

  it('confirms manual payment, marks pending order as paid, refreshes data, and shows success', async () => {
    vi.mocked(getAdminOrderDetail).mockResolvedValueOnce({
      id: 'ord-1',
      reference: 'ET-2026-0007',
      status: 'PAYMENT_PENDING',
      customer: 'Ana Gómez',
      phone: '11223344',
      note: 'Tocar timbre',
      currency: 'ARS',
      subtotal: 9000,
      total: 9500,
      payment: { provider: 'mercadopago', statusDetail: 'pending', externalId: 'pay-1', updatedAt: '2026-05-01T10:00:00Z' },
      items: [{ id: 'line-1', variantId: 'var-1', productName: 'Nuez', unitLabel: '250g', unitPrice: 4500, quantity: 2, subtotal: 9000 }],
      createdAt: '2026-05-01T09:00:00Z',
      updatedAt: '2026-05-01T10:00:00Z',
    })

    render(<AdminOrdersPage />)
    await screen.findByText('ET-2026-0007')
    fireEvent.click(screen.getByRole('button', { name: /Ver detalle ET-2026-0007/i }))
    await screen.findByRole('region', { name: /Detalle pedido ET-2026-0007/i })

    fireEvent.click(screen.getByRole('button', { name: /Marcar como pagado/i }))

    await waitFor(() => expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('marcar como pagado')))
    await waitFor(() => expect(vi.mocked(updateAdminOrderPaymentStatus)).toHaveBeenCalledWith('ord-1', 'PAID'))
    expect(await screen.findByText(/Pago confirmado manualmente/i)).toBeInTheDocument()
    expect(vi.mocked(listAdminOrders)).toHaveBeenCalledTimes(2)
    expect(vi.mocked(getAdminOrderDetail)).toHaveBeenLastCalledWith('ord-1')
  })

  it('exposes manual payment confirmation for draft orders without provider payment state', async () => {
    vi.mocked(getAdminOrderDetail).mockResolvedValueOnce({
      id: 'ord-1',
      reference: 'ET-2026-0007',
      status: 'DRAFT',
      customer: 'Ana Gómez',
      phone: '11223344',
      note: 'Tocar timbre',
      currency: 'ARS',
      subtotal: 9000,
      total: 9500,
      payment: null,
      items: [{ id: 'line-1', variantId: 'var-1', productName: 'Nuez', unitLabel: '250g', unitPrice: 4500, quantity: 2, subtotal: 9000 }],
      createdAt: '2026-05-01T09:00:00Z',
      updatedAt: '2026-05-01T10:00:00Z',
    })

    render(<AdminOrdersPage />)
    await screen.findByText('ET-2026-0007')
    fireEvent.click(screen.getByRole('button', { name: /Ver detalle ET-2026-0007/i }))

    const detail = await screen.findByRole('region', { name: /Detalle pedido ET-2026-0007/i })
    expect(within(detail).getByText('Sin proveedor')).toBeInTheDocument()

    fireEvent.click(within(detail).getByRole('button', { name: /Marcar pago confirmado/i }))

    await waitFor(() => expect(vi.mocked(updateAdminOrderPaymentStatus)).toHaveBeenCalledWith('ord-1', 'PAID'))
    expect(await screen.findByText(/Pago confirmado manualmente/i)).toBeInTheDocument()
  })

  it('shows a clear next-action area for draft orders with payment, cancel, and expire actions', async () => {
    vi.mocked(getAdminOrderDetail).mockResolvedValueOnce(orderDetail({ status: 'DRAFT', payment: null }))

    render(<AdminOrdersPage />)
    await screen.findByText('ET-2026-0007')
    fireEvent.click(screen.getByRole('button', { name: /Ver detalle ET-2026-0007/i }))

    const detail = await screen.findByRole('region', { name: /Detalle pedido ET-2026-0007/i })
    expect(within(detail).getByRole('heading', { name: /Siguiente acción/i })).toBeInTheDocument()
    expect(within(detail).getByRole('button', { name: /Marcar pago confirmado/i })).toBeInTheDocument()
    expect(within(detail).getByRole('button', { name: /Cancelar pedido/i })).toBeInTheDocument()
    expect(within(detail).getByRole('button', { name: /Expirar pedido/i })).toBeInTheDocument()
  })

  it('shows mark preparing for paid orders and calls the admin status endpoint', async () => {
    vi.mocked(getAdminOrderDetail).mockResolvedValueOnce(orderDetail({ status: 'PAID' }))
    vi.mocked(updateAdminOrderStatus).mockResolvedValueOnce(orderDetail({ status: 'PREPARING' }))

    render(<AdminOrdersPage />)
    await screen.findByText('ET-2026-0007')
    fireEvent.click(screen.getByRole('button', { name: /Ver detalle ET-2026-0007/i }))

    const detail = await screen.findByRole('region', { name: /Detalle pedido ET-2026-0007/i })
    fireEvent.click(within(detail).getByRole('button', { name: /Marcar como preparando/i }))

    await waitFor(() => expect(vi.mocked(updateAdminOrderStatus)).toHaveBeenCalledWith('ord-1', 'PREPARING'))
    expect(await screen.findByText(/Estado del pedido actualizado a PREPARING/i)).toBeInTheDocument()
  })

  it('shows mark ready for preparing orders and calls the admin status endpoint', async () => {
    vi.mocked(getAdminOrderDetail).mockResolvedValueOnce(orderDetail({ status: 'PREPARING' }))
    vi.mocked(updateAdminOrderStatus).mockResolvedValueOnce(orderDetail({ status: 'READY' }))

    render(<AdminOrdersPage />)
    await screen.findByText('ET-2026-0007')
    fireEvent.click(screen.getByRole('button', { name: /Ver detalle ET-2026-0007/i }))

    const detail = await screen.findByRole('region', { name: /Detalle pedido ET-2026-0007/i })
    fireEvent.click(within(detail).getByRole('button', { name: /Marcar listo para retirar/i }))

    await waitFor(() => expect(vi.mocked(updateAdminOrderStatus)).toHaveBeenCalledWith('ord-1', 'READY'))
    expect(await screen.findByText(/Estado del pedido actualizado a READY/i)).toBeInTheDocument()
  })

  it('shows mark delivered for ready orders and calls the admin status endpoint', async () => {
    vi.mocked(getAdminOrderDetail).mockResolvedValueOnce(orderDetail({ status: 'READY' }))
    vi.mocked(updateAdminOrderStatus).mockResolvedValueOnce(orderDetail({ status: 'DELIVERED' }))

    render(<AdminOrdersPage />)
    await screen.findByText('ET-2026-0007')
    fireEvent.click(screen.getByRole('button', { name: /Ver detalle ET-2026-0007/i }))

    const detail = await screen.findByRole('region', { name: /Detalle pedido ET-2026-0007/i })
    fireEvent.click(within(detail).getByRole('button', { name: /Marcar como entregado/i }))

    await waitFor(() => expect(vi.mocked(updateAdminOrderStatus)).toHaveBeenCalledWith('ord-1', 'DELIVERED'))
    expect(await screen.findByText(/Estado del pedido actualizado a DELIVERED/i)).toBeInTheDocument()
  })

  it('does not expose manual payment confirmation for terminal orders', async () => {
    vi.mocked(getAdminOrderDetail).mockResolvedValueOnce({
      id: 'ord-1',
      reference: 'ET-2026-0007',
      status: 'CANCELLED',
      customer: 'Ana Gómez',
      phone: '11223344',
      note: 'Tocar timbre',
      currency: 'ARS',
      subtotal: 9000,
      total: 9500,
      payment: null,
      items: [{ id: 'line-1', variantId: 'var-1', productName: 'Nuez', unitLabel: '250g', unitPrice: 4500, quantity: 2, subtotal: 9000 }],
      createdAt: '2026-05-01T09:00:00Z',
      updatedAt: '2026-05-01T10:00:00Z',
    })

    render(<AdminOrdersPage />)
    await screen.findByText('ET-2026-0007')
    fireEvent.click(screen.getByRole('button', { name: /Ver detalle ET-2026-0007/i }))

    const detail = await screen.findByRole('region', { name: /Detalle pedido ET-2026-0007/i })
    expect(within(detail).queryByRole('button', { name: /Marcar pago confirmado/i })).not.toBeInTheDocument()
  })

  it.each([
    ['DELIVERED', 'Entregado'],
    ['CANCELLED', 'Cancelado'],
    ['EXPIRED', 'Expirado'],
  ])('shows terminal state without progression actions for %s orders', async (terminalStatus, terminalLabel) => {
    vi.mocked(getAdminOrderDetail).mockResolvedValueOnce(orderDetail({ status: terminalStatus }))

    render(<AdminOrdersPage />)
    await screen.findByText('ET-2026-0007')
    fireEvent.click(screen.getByRole('button', { name: /Ver detalle ET-2026-0007/i }))

    const detail = await screen.findByRole('region', { name: /Detalle pedido ET-2026-0007/i })
    expect(within(detail).getAllByText(/Estado terminal/i).at(-1)).toHaveTextContent(terminalLabel)
    expect(within(detail).queryByRole('button', { name: /Marcar|Cancelar|Expirar/i })).not.toBeInTheDocument()
  })

  it('shows standardized correlation-aware status update errors', async () => {
    vi.mocked(getAdminOrderDetail).mockResolvedValueOnce({
      id: 'ord-1',
      reference: 'ET-2026-0007',
      status: 'PAYMENT_PENDING',
      customer: 'Ana Gómez',
      phone: '11223344',
      note: 'Tocar timbre',
      currency: 'ARS',
      subtotal: 9000,
      total: 9500,
      payment: { provider: 'mercadopago', statusDetail: 'pending', externalId: 'pay-1', updatedAt: '2026-05-01T10:00:00Z' },
      items: [{ id: 'line-1', variantId: 'var-1', productName: 'Nuez', unitLabel: '250g', unitPrice: 4500, quantity: 2, subtotal: 9000 }],
      createdAt: '2026-05-01T09:00:00Z',
      updatedAt: '2026-05-01T10:00:00Z',
    })
    vi.mocked(updateAdminOrderStatus).mockRejectedValueOnce(new ApiClientError(409, 'Invalid order status transition PAID -> CANCELLED', { code: 'CONFLICT', correlationId: 'corr-order-409' }))

    render(<AdminOrdersPage />)
    await screen.findByText('ET-2026-0007')
    fireEvent.click(screen.getByRole('button', { name: /Ver detalle ET-2026-0007/i }))
    await screen.findByRole('region', { name: /Detalle pedido ET-2026-0007/i })

    fireEvent.click(screen.getByRole('button', { name: /Cancelar pedido/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid order status transition PAID -> CANCELLED')
    expect(screen.getByRole('alert')).toHaveTextContent('corr-order-409')
  })

  it('shows empty, loading, and error states matching admin pages', async () => {
    vi.mocked(listAdminOrders).mockResolvedValueOnce({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
    render(<AdminOrdersPage />)
    expect(await screen.findByRole('heading', { name: /Sin pedidos/i })).toBeInTheDocument()

    vi.mocked(listAdminOrders).mockRejectedValueOnce(new Error('boom'))
    render(<AdminOrdersPage />)
    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo cargar pedidos admin.')
  })
})
