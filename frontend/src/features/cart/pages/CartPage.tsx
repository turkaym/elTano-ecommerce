import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import type { CartItem, CartTotals } from '../../../shared/types/checkout'
import { CartPanel } from '../components/CartPanel'

interface CartPageProps {
  items: CartItem[]
  totals: CartTotals
  warning: string | null
  checkoutContent: ReactNode
  onDismissWarning: () => void
  onSetQty: (variantId: string, quantity: number) => void
  onRemove: (variantId: string) => void
  onClear: () => void
}

const currencyFormatter = new Intl.NumberFormat('es-AR', {
  style: 'currency',
  currency: 'ARS',
  maximumFractionDigits: 0,
})

export function CartPage({
  items,
  totals,
  warning,
  checkoutContent,
  onDismissWarning,
  onSetQty,
  onRemove,
  onClear,
}: CartPageProps) {
  const isEmpty = items.length === 0

  return (
    <main className="main-content cart-page-main">
      <section className="cart-page-hero" aria-labelledby="carrito-title">
        <p className="hero-kicker">Checkout</p>
        <h1 id="carrito-title">Mi carrito</h1>
        <p>Revisá tus productos y finalizá la compra cuando esté todo listo.</p>
      </section>

      {isEmpty ? (
        <section className="section cart-empty-state" aria-labelledby="carrito-vacio-title">
          <h2 id="carrito-vacio-title">Tu carrito está vacío</h2>
          <p>Agregá productos del catálogo para armar tu pedido.</p>
          <Link className="btn btn-primary" to="/productos">
            Ver productos
          </Link>
        </section>
      ) : (
        <div className="cart-page-grid">
          <CartPanel
            items={items}
            totals={totals}
            warning={warning}
            onDismissWarning={onDismissWarning}
            onSetQty={onSetQty}
            onRemove={onRemove}
            onClear={onClear}
            showSummary={false}
          />

          <aside className="cart-page-summary" aria-labelledby="cart-summary-title">
            <h2 id="cart-summary-title">Resumen</h2>
            <p className="cart-summary-row">
              <span>Productos</span>
              <strong>{totals.itemCount}</strong>
            </p>
            <p className="cart-summary-row">
              <span>Subtotal</span>
              <strong>{currencyFormatter.format(totals.subtotal)}</strong>
            </p>
            <p className="cart-summary-row cart-summary-total">
              <span>Total</span>
              <strong>{currencyFormatter.format(totals.total)}</strong>
            </p>
            <Link className="btn btn-secondary" to="/productos">
              Seguir comprando
            </Link>
            <a className="btn btn-primary" href="#checkout-title">
              Finalizar compra
            </a>
          </aside>
        </div>
      )}

      <div className="cart-page-checkout">{checkoutContent}</div>
    </main>
  )
}
