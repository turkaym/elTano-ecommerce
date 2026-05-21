export const checkoutMvpEnabled = import.meta.env.VITE_CHECKOUT_MVP_ENABLED !== 'false'
export const checkoutPaymentEnabled = import.meta.env.VITE_CHECKOUT_PAYMENT_ENABLED === 'true'
export const storefrontVariantFlowEnabled = import.meta.env.VITE_STOREFRONT_VARIANT_FLOW_ENABLED !== 'false'
export const adminDashboardEnabled = import.meta.env.VITE_ADMIN_ENABLED !== 'false'
export const deliverySurchargeFromAmount = parseNumberFlag(import.meta.env.VITE_DELIVERY_SURCHARGE_FROM_AMOUNT, 3500)

function parseNumberFlag(value: string | undefined, fallback: number) {
  if (value === undefined || value.trim() === '') {
    return fallback
  }

  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : fallback
}
