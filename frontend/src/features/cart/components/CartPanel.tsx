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
    <section id="carrito" className="section" aria-labelledby="carrito-title">
      <div className="section-header section-header-inline">
        <h2 id="carrito-title" tabIndex={-1}>Carrito</h2>
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
              <li key={item.variantId} className="cart-item" aria-label={item.productName}>
                <div className="cart-item-media" aria-hidden={item.imageUrl ? undefined : true}>
                  {item.imageUrl ? (
                    <img src={item.imageUrl} alt={item.imageAltText ?? item.productName} />
                  ) : (
                    <span className="cart-item-media-placeholder">Imagen no disponible</span>
                  )}
                </div>
                <div className="cart-item-header">
                  {item.categoryName ? <p className="cart-item-category">{item.categoryName}</p> : null}
                  <h3>{item.productName}</h3>
                  <p>{item.unitLabel}</p>
                </div>
                <div className="cart-item-controls">
                  <div className="cart-item-stepper" aria-label={`Cantidad de ${item.productName}`}>
                    <button
                      type="button"
                      className="cart-stepper-btn"
                      aria-label={`Restar ${item.productName}`}
                      disabled={item.quantity <= 1}
                      onClick={() => onSetQty(item.variantId, item.quantity - 1)}
                    >
                      −
                    </button>
                    <span className="cart-item-qty-value" aria-live="polite">{item.quantity}</span>
                    <button
                      type="button"
                      className="cart-stepper-btn"
                      aria-label={`Sumar ${item.productName}`}
                      disabled={item.quantity >= item.stockAvailable}
                      onClick={() => onSetQty(item.variantId, item.quantity + 1)}
                    >
                      +
                    </button>
                  </div>
                  <strong className="cart-item-line-total">{currencyFormatter.format(item.price * item.quantity)}</strong>
                  <button
                    type="button"
                    className="link-btn"
                    aria-label={`Quitar ${item.productName}`}
                    onClick={() => onRemove(item.variantId)}
                  >
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
