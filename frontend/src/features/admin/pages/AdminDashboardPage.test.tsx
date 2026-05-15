import { render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { AdminDashboardPage } from './AdminDashboardPage'
import { listAdminOrders, listAdminProducts } from '../services/adminOperationsService'

vi.mock('../services/adminOperationsService', () => ({
  listAdminOrders: vi.fn(),
  listAdminProducts: vi.fn(),
}))

const today = new Date('2026-05-13T15:00:00-03:00')

describe('AdminDashboardPage', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    vi.setSystemTime(today)
    vi.mocked(listAdminProducts).mockResolvedValue([
      {
        id: 'p-1',
        name: 'Almendras',
        productType: 'GRANEL',
        inventoryPolicy: 'BULK_WEIGHT',
        stockBaseGrams: 950,
        stockReservedBaseGrams: 100,
        active: true,
        variants: [{ id: 'v-1', unitLabel: '250g', price: 2500, stockAvailable: 0 }],
      },
      {
        id: 'p-2',
        name: 'Aceite de oliva',
        productType: 'ENVASADO',
        inventoryPolicy: 'PER_VARIANT',
        active: true,
        variants: [{ id: 'v-2', unitLabel: '500ml', price: 7200, stockAvailable: 0 }],
      },
      {
        id: 'p-3',
        name: 'Yerba mate',
        productType: 'UNIDAD',
        inventoryPolicy: 'PER_VARIANT',
        active: true,
        variants: [{ id: 'v-3', unitLabel: 'unidad', price: 1800, stockAvailable: 3 }],
      },
    ])
    vi.mocked(listAdminOrders).mockResolvedValue({
      items: [
        {
          id: 'o-1',
          reference: 'ET-1001',
          customer: 'Ana Pérez',
          status: 'PAID',
          paymentStatus: 'approved',
          total: 12000,
          createdAt: '2026-05-13T12:20:00-03:00',
        },
        {
          id: 'o-2',
          reference: 'ET-1000',
          customer: 'Luis Gómez',
          status: 'PAYMENT_PENDING',
          paymentStatus: 'pending',
          total: 3500,
          createdAt: '2026-05-12T17:00:00-03:00',
        },
      ],
      page: 0,
      size: 8,
      totalElements: 2,
      totalPages: 1,
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('renders operational metric cards from admin orders and products', async () => {
    render(<AdminDashboardPage />, { wrapper: MemoryRouter })

    expect(await screen.findByRole('heading', { name: 'Dashboard admin' })).toBeInTheDocument()

    expect(screen.getByRole('article', { name: /Pedidos de hoy/i })).toHaveTextContent('1')
    expect(screen.getByRole('article', { name: /Ventas confirmadas de hoy/i })).toHaveTextContent('$ 12.000,00')
    expect(screen.getByRole('article', { name: /Pendientes de pago/i })).toHaveTextContent('1')
    expect(screen.getByRole('article', { name: /Pedidos para preparar/i })).toHaveTextContent('1')
    expect(screen.getByRole('article', { name: /Productos sin stock/i })).toHaveTextContent('1')
    expect(screen.getByRole('article', { name: /Productos con stock bajo/i })).toHaveTextContent('2')
  })

  it('shows stock and pending-payment alerts with granel stock formatted as kg and grams', async () => {
    render(<AdminDashboardPage />, { wrapper: MemoryRouter })

    const alerts = await screen.findByRole('region', { name: /Alertas y atención/i })
    expect(within(alerts).getByText(/Almendras/)).toBeInTheDocument()
    expect(within(alerts).getByText(/850 g disponibles/i)).toBeInTheDocument()
    const outOfStockAlert = within(alerts).getByRole('article', { name: /Alerta stock Aceite de oliva/i })
    expect(outOfStockAlert).toHaveTextContent('Aceite de oliva')
    expect(outOfStockAlert).toHaveTextContent('Sin stock')
    expect(within(alerts).getByRole('link', { name: /Revisar pedido ET-1000/i })).toHaveAttribute('href', '/admin/pedidos')
  })

  it('lists recent orders with details and order-page link', async () => {
    render(<AdminDashboardPage />, { wrapper: MemoryRouter })

    const recentOrders = await screen.findByRole('region', { name: /Pedidos recientes/i })
    expect(within(recentOrders).getByRole('article', { name: /Pedido ET-1001/i })).toHaveTextContent('Ana Pérez')
    expect(within(recentOrders).getByRole('article', { name: /Pedido ET-1001/i })).toHaveTextContent('PAID')
    expect(within(recentOrders).getByRole('article', { name: /Pedido ET-1001/i })).toHaveTextContent('approved')
    expect(within(recentOrders).getByRole('article', { name: /Pedido ET-1001/i })).toHaveTextContent('$ 12.000,00')
    expect(within(recentOrders).getByRole('link', { name: /Ver todos los pedidos/i })).toHaveAttribute('href', '/admin/pedidos')
  })

  it('renders quick links to admin workflows', async () => {
    render(<AdminDashboardPage />, { wrapper: MemoryRouter })

    const links = await screen.findByRole('navigation', { name: /Accesos rápidos admin/i })
    expect(within(links).getByRole('link', { name: /Productos/i })).toHaveAttribute('href', '/admin/productos')
    expect(within(links).getByRole('link', { name: /Categorías/i })).toHaveAttribute('href', '/admin/categorias')
    expect(within(links).getByRole('link', { name: /Pedidos/i })).toHaveAttribute('href', '/admin/pedidos')
    expect(within(links).getByRole('link', { name: /Catalog Jobs/i })).toHaveAttribute('href', '/admin/catalog-jobs')
  })
})
