import { useEffect, useState } from 'react'
import { useLocation, useSearchParams } from 'react-router-dom'
import { AppRoutes } from './routes/AppRoutes'
import { PaymentReturnStatus } from '../features/checkout/components/PaymentReturnStatus'
import { CartPanel } from '../features/cart/components/CartPanel'
import { useCartStore } from '../features/cart/state/cartStore'
import {
  FeaturedProductsSection,
  type FeaturedAddToCartPayload,
} from '../features/catalog/components/FeaturedProductsSection'
import { getFeaturedProducts } from '../features/catalog/services/catalogService'
import { CheckoutForm, type CheckoutFormValues } from '../features/checkout/components/CheckoutForm'
import { createOrderDraft, startDraftPayment } from '../features/checkout/services/checkoutService'
import { StorefrontNav } from '../features/navigation/components/StorefrontNav'
import { SearchBar } from '../features/search/components/SearchBar'
import { ApiClientError } from '../shared/api/httpClient'
import {
  checkoutMvpEnabled,
  checkoutPaymentEnabled,
} from '../shared/config/flags'
import type { FeaturedProduct } from '../shared/types/catalog'
import type { CreateOrderDraftRequest } from '../shared/types/checkout'

const whatsappPhone = '5491123456789'
const paymentDraftMessageStorageKey = 'checkout-payment-draft-messages'
const uuidRegex = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i

const benefits = [
  {
    title: 'Envio local rapido',
    description: 'Coordinamos entregas en el dia dentro de la zona para que recibas todo fresco.',
  },
  {
    title: 'Retiro por el local',
    description: 'Hace tu pedido y pasa a buscarlo sin espera en el horario que te quede comodo.',
  },
  {
    title: 'Productos saludables',
    description: 'Seleccion de frutos secos, semillas y harinas para sumar nutricion a tu rutina.',
  },
]

function createWhatsappLink(message: string) {
  const encodedMessage = encodeURIComponent(message)
  return `https://wa.me/${whatsappPhone}?text=${encodedMessage}`
}

function isUuid(value: string) {
  return uuidRegex.test(value)
}

export function App() {
  const location = useLocation()
  const [searchParams, setSearchParams] = useSearchParams()
  const [showBrandLogo, setShowBrandLogo] = useState(true)
  const [products, setProducts] = useState<FeaturedProduct[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [source, setSource] = useState<'api' | 'mock'>('mock')
  const [submitError, setSubmitError] = useState<string | null>(null)
  const cart = useCartStore()
  const hasInvalidVariantIds = cart.items.some((item) => !isUuid(item.variantId))
  const usingMockCatalog = source === 'mock'
  const checkoutBlockedMessage = usingMockCatalog
    ? 'No podemos finalizar el pedido con productos de muestra. Espera a que cargue el catalogo real del backend.'
    : hasInvalidVariantIds
      ? 'Tu carrito contiene productos incompatibles con el backend. Vacialo y vuelve a agregar productos del catalogo real.'
      : null

  const returnParams = new URLSearchParams(location.search)
  const returnDraftId = returnParams.get('draftId')
  const providerStatusHint = returnParams.get('status') ?? returnParams.get('result')
  const searchValue = searchParams.get('q') ?? ''

  function handleSearchChange(nextValue: string) {
    const nextParams = new URLSearchParams(searchParams)
    if (nextValue.trim()) {
      nextParams.set('q', nextValue)
    } else {
      nextParams.delete('q')
    }

    setSearchParams(nextParams)
  }

  useEffect(() => {
    let isMounted = true

    async function loadFeaturedProducts() {
      try {
        const result = await getFeaturedProducts(6)
        if (!isMounted) {
          return
        }

        setProducts(result.products)
        setSource(result.source)
      } finally {
        if (isMounted) {
          setIsLoading(false)
        }
      }
    }

    void loadFeaturedProducts()

    return () => {
      isMounted = false
    }
  }, [])

  function handleAddToCart(payload: FeaturedAddToCartPayload) {
    const selectedProduct = products.find((product) => product.id === payload.productId)
    const selectedVariant = selectedProduct?.variants.find((variant) => variant.id === payload.variantId)
    if (!selectedProduct || !selectedVariant) {
      return
    }

    cart.addItem({
      variantId: selectedVariant.id,
      productName: selectedProduct.name,
      unitLabel: selectedVariant.unitLabel,
      price: selectedVariant.price,
      stockAvailable: selectedVariant.stockAvailable,
      quantity: payload.quantity,
    })
  }

  async function handleSubmitDraft(values: CheckoutFormValues) {
    if (checkoutBlockedMessage) {
      setSubmitError(checkoutBlockedMessage)
      return
    }

    const payload: CreateOrderDraftRequest = {
      customerName: values.customerName,
      phone: values.phone,
      note: values.note.trim() ? values.note : undefined,
      items: cart.items.map((item) => ({
        variantId: item.variantId,
        quantity: item.quantity,
      })),
    }

    try {
      const response = await createOrderDraft(payload)
      setSubmitError(null)
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
      const popup = window.open(createWhatsappLink(response.whatsappMessage), '_blank', 'noopener,noreferrer')
      if (!popup) {
        setSubmitError('No pudimos abrir WhatsApp automaticamente. Habilita popups e intenta de nuevo.')
      }
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

  const homeContent = (
    <>
      <header className="hero">
        <img className="hero-image" src="/heroimage.png" alt="Productos naturales El Tano" />
        <div className="hero-content">
          <div className="hero-actions">
            <a className="btn btn-primary" href="#productos-destacados-title">
              Comprar ahora
            </a>
          </div>
        </div>
      </header>

      <main className="main-content">
        <section className="section" aria-labelledby="beneficios-title">
          <div className="section-header">
            <h2 id="beneficios-title">Por que nos eligen</h2>
            <p>Compra simple, entrega comoda y productos pensados para alimentarte mejor.</p>
          </div>
          <div className="benefits-grid">
            {benefits.map((benefit) => (
              <article key={benefit.title} className="benefit-card">
                <h3>{benefit.title}</h3>
                <p>{benefit.description}</p>
              </article>
            ))}
          </div>
        </section>

        <FeaturedProductsSection
          products={products}
          isLoading={isLoading}
          source={source}
          onAddToCart={handleAddToCart}
        />

        {checkoutMvpEnabled ? (
          <div className="checkout-grid">
            <CartPanel
              items={cart.items}
              totals={cart.totals}
              warning={cart.warning}
              onDismissWarning={cart.dismissWarning}
              onSetQty={cart.setQty}
              onRemove={cart.removeItem}
              onClear={cart.clear}
            />
            <CheckoutForm
              isCartEmpty={!cart.items.length}
              isSubmitBlocked={Boolean(checkoutBlockedMessage)}
              blockedSubmitMessage={checkoutBlockedMessage ?? undefined}
              submitError={submitError}
              submitLabel={checkoutPaymentEnabled ? 'Iniciar pago online' : undefined}
              onSubmitDraft={handleSubmitDraft}
            />
          </div>
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
        )}
      </main>
    </>
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
      <div className="app-chrome">
        <header className="top-shell">
          <div className="top-shell-brand" aria-label="Marca El Tano Frutos Secos">
            {showBrandLogo ? (
              <img
                className="brand-logo"
                src="/logo-el-tano.png"
                alt="El Tano Frutos Secos"
                onError={() => setShowBrandLogo(false)}
              />
            ) : (
              <p className="hero-kicker">El Tano Frutos Secos</p>
            )}
          </div>

          <div className="top-shell-right">
            <StorefrontNav />

            <div className="top-shell-icons" aria-label="Accesos de cuenta y carrito">
              <span aria-hidden="true">👤</span>
              <span aria-hidden="true">🛒</span>
            </div>
          </div>
        </header>

        <SearchBar value={searchValue} onChange={handleSearchChange} />
      </div>

      <AppRoutes homeContent={homeContent} checkoutReturnContent={checkoutReturnContent} />
    </div>
  )
}
