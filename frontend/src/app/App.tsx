import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { AppRoutes } from './routes/AppRoutes'
import { PaymentReturnStatus } from '../features/checkout/components/PaymentReturnStatus'
import { CartPage } from '../features/cart/pages/CartPage'
import { useCartStore } from '../features/cart/state/cartStore'
import { ProductsPage } from '../features/catalog/pages/ProductsPage'
import { CheckoutForm, type CheckoutFormValues } from '../features/checkout/components/CheckoutForm'
import { createOrderDraft, startDraftPayment } from '../features/checkout/services/checkoutService'
import { ApiClientError } from '../shared/api/httpClient'
import {
  checkoutMvpEnabled,
  checkoutPaymentEnabled,
} from '../shared/config/flags'
import type { CreateOrderDraftRequest } from '../shared/types/checkout'

const whatsappPhone = '5492966659577'
const paymentDraftMessageStorageKey = 'checkout-payment-draft-messages'
const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

function createWhatsappLink(message: string) {
  const encodedMessage = encodeURIComponent(message)
  return `https://wa.me/${whatsappPhone}?text=${encodedMessage}`
}

function isUuid(value: string) {
  return uuidRegex.test(value)
}

export function App() {
  const location = useLocation()
  const [showBrandLogo, setShowBrandLogo] = useState(true)
  const [submitError, setSubmitError] = useState<string | null>(null)
  const cart = useCartStore()
  const hasInvalidVariantIds = cart.items.some((item) => !isUuid(item.variantId))
  const checkoutBlockedMessage = hasInvalidVariantIds
      ? 'Tu carrito contiene productos incompatibles con el backend. Vacialo y vuelve a agregar productos del catalogo real.'
      : null

  const returnParams = new URLSearchParams(location.search)
  const returnDraftId = returnParams.get('draftId')
  const providerStatusHint = returnParams.get('status') ?? returnParams.get('result')
  const isAdminRoute = location.pathname.startsWith('/admin')
  const cartItemLabel = cart.totals.itemCount === 1 ? 'item' : 'items'

  async function handleSubmitDraft(values: CheckoutFormValues) {
    if (checkoutBlockedMessage) {
      setSubmitError(checkoutBlockedMessage)
      return
    }

    const payload: CreateOrderDraftRequest = {
      customerName: values.customerName,
      phone: values.phone,
      note: values.note.trim() ? values.note : undefined,
      fulfillmentMethod: values.fulfillmentMethod,
      deliveryAddress: values.fulfillmentMethod === 'DELIVERY' ? values.deliveryAddress : undefined,
      pickupTime: values.fulfillmentMethod === 'PICKUP' ? values.pickupTime : undefined,
      items: cart.items.map((item) => ({
        variantId: item.variantId,
        quantity: item.quantity,
      })),
    }

    try {
      setSubmitError(null)
      const response = await createOrderDraft(payload)
      if (checkoutPaymentEnabled) {
        const payment = await startDraftPayment(response.draftId)
        const existing = window.sessionStorage.getItem(paymentDraftMessageStorageKey)
        const draftMessages: Record<string, string> = existing ? JSON.parse(existing) : {}
        draftMessages[response.draftId] = response.whatsappMessage
        window.sessionStorage.setItem(paymentDraftMessageStorageKey, JSON.stringify(draftMessages))
        const popup = window.open(payment.initPoint, '_self')
        if (!popup) {
          setSubmitError('No pudimos redirigirte al pago online. Intenta nuevamente.')
        }
        return
      }

      cart.clear()
      window.open(createWhatsappLink(response.whatsappMessage), '_blank', 'noopener,noreferrer')
    } catch (error) {
      if (error instanceof ApiClientError) {
        if (error.status === 409) {
          setSubmitError('No hay stock suficiente para uno o mas productos del carrito.')
          return
        }

        setSubmitError(error.message)
        return
      }

      setSubmitError('No pudimos crear tu pedido. Intenta nuevamente en unos minutos.')
    }
  }

  function continuePaidFlow(draftId: string) {
    const existing = window.sessionStorage.getItem(paymentDraftMessageStorageKey)
    const draftMessages: Record<string, string> = existing ? JSON.parse(existing) : {}
    const message = draftMessages[draftId] ?? `Hola, confirmo el pedido ${draftId}.`
    const popup = window.open(createWhatsappLink(message), '_blank', 'noopener,noreferrer')
    if (!popup) {
      setSubmitError('No pudimos abrir WhatsApp automaticamente. Habilita popups e intenta de nuevo.')
    }
  }

  async function retryPayment(draftId: string) {
    try {
      const payment = await startDraftPayment(draftId)
      const popup = window.open(payment.initPoint, '_self')
      if (!popup) {
        setSubmitError('No pudimos redirigirte al pago online. Intenta nuevamente.')
      }
    } catch {
      setSubmitError('No pudimos reiniciar el pago en este momento.')
    }
  }

  const homeContent = <ProductsPage onAddCartItem={cart.addItem} />

  const checkoutContent = checkoutMvpEnabled ? (
    <CheckoutForm
      isCartEmpty={!cart.items.length}
      isSubmitBlocked={Boolean(checkoutBlockedMessage)}
      blockedSubmitMessage={checkoutBlockedMessage ?? undefined}
      submitError={submitError}
      submitLabel={checkoutPaymentEnabled ? 'Iniciar pago online' : undefined}
      onSubmitDraft={handleSubmitDraft}
    />
  ) : (
    <section className="section checkout-notice" aria-labelledby="compra-title">
      <h2 id="compra-title">Confirma tu compra por WhatsApp</h2>
      <p>Checkout MVP deshabilitado temporalmente. Contactanos por WhatsApp para confirmar.</p>
      <a
        className="btn btn-primary"
        href={createWhatsappLink('Hola! Quiero confirmar una compra en El Tano Frutos Secos.')}
        target="_blank"
        rel="noreferrer"
      >
        Confirmar compra por WhatsApp
      </a>
    </section>
  )

  const cartContent = (
    <CartPage
      items={cart.items}
      totals={cart.totals}
      warning={cart.warning}
      checkoutContent={checkoutContent}
      onDismissWarning={cart.dismissWarning}
      onSetQty={cart.setQty}
      onRemove={cart.removeItem}
      onClear={cart.clear}
    />
  )

  const checkoutReturnContent = checkoutPaymentEnabled ? (
    returnDraftId ? (
      <main className="main-content">
        <PaymentReturnStatus
          draftId={returnDraftId}
          providerStatusHint={providerStatusHint}
          onPaidContinue={() => continuePaidFlow(returnDraftId)}
          onRetry={() => retryPayment(returnDraftId)}
        />
      </main>
    ) : (
      <main className="main-content">
        <section className="section" aria-labelledby="return-error-title">
          <h2 id="return-error-title">No encontramos el borrador de pago</h2>
          <p>Volve al checkout y genera un nuevo intento de pago.</p>
        </section>
      </main>
    )
  ) : (
    <main className="main-content">
      <section className="section" aria-labelledby="payment-disabled-title">
        <h2 id="payment-disabled-title">Pago online no disponible</h2>
        <p>Volvé al inicio para continuar tu compra por WhatsApp.</p>
      </section>
    </main>
  )

  return (
    <div className="app-shell">
      {!isAdminRoute ? (
        <div className="app-chrome">
          <header className="top-shell">
            <Link className="top-shell-brand" to="/" aria-label="Ir al inicio">
              {showBrandLogo ? (
                <img
                  className="brand-logo"
                  src="/elTanoLogo.png"
                  alt="El Tano Frutos Secos"
                  onError={() => setShowBrandLogo(false)}
                />
              ) : (
                <p className="hero-kicker">El Tano Frutos Secos</p>
              )}
            </Link>

            <div className="top-shell-right">
              <div className="top-shell-icons" aria-label="Accesos de cuenta y carrito">
                <span aria-hidden="true">
                  <img src="/user.svg" alt="" />
                </span>
                <Link
                  className="cart-icon-link cart-icon-with-badge"
                  to="/carrito"
                  aria-label={`Ver carrito, ${cart.totals.itemCount} ${cartItemLabel}`}
                >
                  <img src="/cart.svg" alt="" aria-hidden="true" />
                  {cart.totals.itemCount > 0 ? (
                    <span className="cart-icon-badge" aria-hidden="true">
                      {cart.totals.itemCount}
                    </span>
                  ) : null}
                </Link>
              </div>
            </div>
          </header>
        </div>
      ) : null}

      <AppRoutes
        homeContent={homeContent}
        cartContent={cartContent}
        checkoutReturnContent={checkoutReturnContent}
        onCatalogAddToCart={cart.addItem}
      />
    </div>
  )
}
