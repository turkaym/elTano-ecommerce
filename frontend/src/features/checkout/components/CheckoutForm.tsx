import { useState, type FormEvent } from 'react'

interface CheckoutFormValues {
  customerName: string
  phone: string
  note: string
}

interface CheckoutFormProps {
  isCartEmpty: boolean
  isSubmitBlocked?: boolean
  blockedSubmitMessage?: string
  submitError: string | null
  submitLabel?: string
  onSubmitDraft: (values: CheckoutFormValues) => Promise<void>
}

type CheckoutErrors = Partial<Record<keyof CheckoutFormValues | 'cart' | 'checkout', string>>

function validate(values: CheckoutFormValues, isCartEmpty: boolean, isSubmitBlocked: boolean, blockedSubmitMessage?: string): CheckoutErrors {
  const errors: CheckoutErrors = {}

  if (!values.customerName.trim()) {
    errors.customerName = 'Ingresa tu nombre y apellido.'
  }

  if (!values.phone.trim()) {
    errors.phone = 'Ingresa un telefono de contacto.'
  }

  if (isCartEmpty) {
    errors.cart = 'Agrega al menos un producto para continuar.'
  }

  if (isSubmitBlocked) {
    errors.checkout = blockedSubmitMessage ?? 'No es posible enviar el checkout en este momento.'
  }

  return errors
}

export function CheckoutForm({
  isCartEmpty,
  isSubmitBlocked = false,
  blockedSubmitMessage,
  submitError,
  submitLabel = 'Crear pedido y confirmar por WhatsApp',
  onSubmitDraft,
}: CheckoutFormProps) {
  const [values, setValues] = useState<CheckoutFormValues>({
    customerName: '',
    phone: '',
    note: '',
  })
  const [errors, setErrors] = useState<CheckoutErrors>({})
  const [isSubmitting, setIsSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const nextErrors = validate(values, isCartEmpty, isSubmitBlocked, blockedSubmitMessage)
    setErrors(nextErrors)

    if (Object.keys(nextErrors).length > 0) {
      return
    }

    setIsSubmitting(true)
    try {
      await onSubmitDraft(values)
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <section className="section" aria-labelledby="checkout-title">
      <h2 id="checkout-title">Finalizar pedido</h2>
      <p className="checkout-lead">Completamos tu borrador y continuas con el siguiente paso de confirmacion.</p>

      <form className="checkout-form" onSubmit={handleSubmit} noValidate>
        <div className="checkout-field">
          <label className="checkout-field-label" htmlFor="checkout-customer-name">Nombre y apellido *</label>
          <input
            id="checkout-customer-name"
            type="text"
            value={values.customerName}
            onChange={(event) => setValues((prev) => ({ ...prev, customerName: event.target.value }))}
          />
          <small className="checkout-field-helper">Ej: Maria Perez</small>
          {errors.customerName ? <span className="checkout-field-error" role="alert">{errors.customerName}</span> : null}
        </div>

        <div className="checkout-field">
          <label className="checkout-field-label" htmlFor="checkout-phone">Telefono *</label>
          <input
            id="checkout-phone"
            type="tel"
            value={values.phone}
            onChange={(event) => setValues((prev) => ({ ...prev, phone: event.target.value }))}
          />
          <small className="checkout-field-helper">Con codigo de area para poder contactarte.</small>
          {errors.phone ? <span className="checkout-field-error" role="alert">{errors.phone}</span> : null}
        </div>

        <div className="checkout-field">
          <label className="checkout-field-label" htmlFor="checkout-note">Nota (opcional)</label>
          <textarea
            id="checkout-note"
            value={values.note}
            rows={3}
            onChange={(event) => setValues((prev) => ({ ...prev, note: event.target.value }))}
          />
          <small className="checkout-field-helper">Aclaraciones para preparar tu pedido.</small>
        </div>

        <div className="checkout-feedback-stack" aria-live="polite">
          {errors.cart ? <p className="form-error" role="alert">{errors.cart}</p> : null}
          {errors.checkout && !isSubmitBlocked ? <p className="form-error" role="alert">{errors.checkout}</p> : null}
          {submitError ? <p className="form-error" role="alert">{submitError}</p> : null}
        </div>

        <div className="checkout-actions">
          <button type="submit" className="btn btn-primary" disabled={isSubmitting || isCartEmpty || isSubmitBlocked}>
            {isSubmitting ? 'Creando borrador...' : submitLabel}
          </button>
        </div>
      </form>
    </section>
  )
}

export type { CheckoutFormValues }
