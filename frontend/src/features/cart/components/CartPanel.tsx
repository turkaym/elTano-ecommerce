import type { CartItem, CartTotals } from '../../../shared/types/checkout'

interface CartPanelProps {
  items: CartItem[]
  totals: CartTotals
  warning: string | null
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

export function CartPanel({
  items,
  totals,
  warning,
  onDismissWarning,
  onSetQty,
  onRemove,
  onClear,
}: CartPanelProps) {
  return (
    <section className="section" aria-labelledby="carrito-title">
      <div className="section-header section-header-inline">
        <h2 id="carrito-title">Carrito</h2>
        <span className="cart-count">{totals.itemCount} item(s)</span>
      </div>

      {warning ? (
        <p className="cart-warning" role="alert">
          {warning}{' '}
          <button type="button" className="link-btn" onClick={onDismissWarning}>
            Entendido
          </button>
        </p>
      ) : null}

      {!items.length ? (
        <p className="cart-empty">Tu carrito esta vacio.</p>
      ) : (
        <>
          <ul className="cart-list">
            {items.map((item) => (
              <li key={item.variantId} className="cart-item">
                <div className="cart-item-header">
                  <h3>{item.productName}</h3>
                  <p>{item.unitLabel}</p>
                </div>
                <div className="cart-item-controls">
                  <label className="cart-item-qty">
                    Cant.
                    <input
                      className="cart-item-qty-input"
                      type="number"
                      min={1}
                      max={item.stockAvailable}
                      value={item.quantity}
                      onChange={(event) => onSetQty(item.variantId, Number(event.target.value))}
                    />
                  </label>
                  <strong className="cart-item-line-total">{currencyFormatter.format(item.price * item.quantity)}</strong>
                  <button type="button" className="link-btn" onClick={() => onRemove(item.variantId)}>
                    Quitar
                  </button>
                </div>
              </li>
            ))}
          </ul>

          <div className="cart-summary">
            <p className="cart-summary-row">
              <span>Subtotal</span>
              <strong>{currencyFormatter.format(totals.subtotal)}</strong>
            </p>
            <p className="cart-summary-row cart-summary-total">
              <span>Total</span>
              <strong>{currencyFormatter.format(totals.total)}</strong>
            </p>
          </div>

          <button type="button" className="btn btn-secondary" onClick={onClear}>
            Vaciar carrito
          </button>
        </>
      )}
    </section>
  )
}
