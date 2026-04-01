/**
 * Client-side billing / Stripe visibility. Must stay aligned with backend {@code app.billing.enabled}
 * (and {@code APP_BILLING_ENABLED}). When billing is off, the UI hides checkout, portal, and upgrade CTAs.
 *
 * After login, prefer {@link SubscriptionStatusApi.billingEnabled} from GET /api/subscription/status
 * so the server remains the source of truth.
 */
export function isBillingEnabledFromEnv(): boolean {
  return process.env.NEXT_PUBLIC_BILLING_ENABLED === 'true';
}

/** Effective flag when API has not loaded yet or omitted the field (older backends). */
export function billingEnabledOrEnv(apiValue: boolean | undefined): boolean {
  if (typeof apiValue === 'boolean') return apiValue;
  return isBillingEnabledFromEnv();
}

/**
 * Whether checkout/portal/upgrade CTAs should appear. Explicit server flags win.
 * When the API response omits billing fields (older backends), default to true so Stripe flows keep working.
 * When {@code api} is null (not loaded yet), {@link isBillingEnabledFromEnv} applies.
 */
export function paymentActionsAvailableFromApi(
  api: { paymentActionsAvailable?: boolean; billingEnabled?: boolean } | null | undefined
): boolean {
  if (api == null) return isBillingEnabledFromEnv();
  if (api.paymentActionsAvailable === false) return false;
  if (api.paymentActionsAvailable === true) return true;
  if (api.billingEnabled === false) return false;
  if (api.billingEnabled === true) return true;
  return true;
}
