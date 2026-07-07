import { getApiBaseUrl, getAuthHeaders, resolveApiBaseUrl } from './api-client';
import type {
  PublicPlanLimitsResponse,
  SubscriptionStatus,
  SubscriptionStatusApi,
  SyncFromSessionResult,
} from './api-types';

export function deleteModalWebsiteScanNote(status: SubscriptionStatus | null): string {
  const n = status?.websiteScansRemaining;
  const base =
    'Deleting this chatbot does not reset your website scan limits—you will not get extra scans by creating a new one.';
  if (typeof n === 'number' && Number.isFinite(n)) {
    if (n <= 0) {
      return `${base} You currently have no website scans left on your account (monthly quota + daily cap still apply).`;
    }
    return `${base} Right now you have ${n} website scan${n === 1 ? '' : 's'} left on your account (monthly quota + daily cap, whichever is stricter).`;
  }
  return `${base} Scan limits stay tied to your account.`;
}

export function websiteScanFieldsFromSubscriptionApi(
  api: SubscriptionStatusApi | null
): Pick<
  SubscriptionStatus,
  | 'websiteScansRemaining'
  | 'websiteScansMonthlyQuota'
  | 'websiteScansUsedThisMonth'
  | 'websiteScansDailyLimit'
  | 'websiteScansUsedRollingDay'
> {
  if (!api || typeof api.websiteScansRemaining !== 'number' || !Number.isFinite(api.websiteScansRemaining)) {
    return {};
  }
  return {
    websiteScansRemaining: api.websiteScansRemaining,
    websiteScansMonthlyQuota:
      typeof api.websiteScansMonthlyQuota === 'number' ? api.websiteScansMonthlyQuota : undefined,
    websiteScansUsedThisMonth:
      typeof api.websiteScansUsedThisMonth === 'number' ? api.websiteScansUsedThisMonth : undefined,
    websiteScansDailyLimit: typeof api.websiteScansDailyLimit === 'number' ? api.websiteScansDailyLimit : undefined,
    websiteScansUsedRollingDay:
      typeof api.websiteScansUsedRollingDay === 'number' ? api.websiteScansUsedRollingDay : undefined,
  };
}

export async function fetchPublicPlanLimits(): Promise<PublicPlanLimitsResponse | null> {
  try {
    const response = await fetch(`${getApiBaseUrl()}/api/plans/limits`, {
      method: 'GET',
      credentials: 'omit',
    });
    if (!response.ok) return null;
    return (await response.json()) as PublicPlanLimitsResponse;
  } catch {
    return null;
  }
}

export async function getSubscriptionStatusFromApi(): Promise<SubscriptionStatusApi | null> {
  try {
    const headers = getAuthHeaders();
    const response = await fetch(`${resolveApiBaseUrl()}/api/subscription/status`, {
      method: 'GET',
      credentials: 'include',
      headers,
      cache: 'no-store',
    });
    if (!response.ok) return null;
    return await response.json();
  } catch {
    return null;
  }
}

function isStripeCheckoutUrl(url: string): boolean {
  try {
    const u = new URL(url);
    return u.protocol === 'https:' && u.hostname === 'checkout.stripe.com';
  } catch {
    return false;
  }
}

function isStripePortalUrl(url: string): boolean {
  try {
    const u = new URL(url);
    return u.protocol === 'https:' && u.hostname === 'billing.stripe.com';
  } catch {
    return false;
  }
}

export async function createCheckoutSession(plan?: 'BASIC' | 'PRO' | 'ENTERPRISE'): Promise<string> {
  const headers = getAuthHeaders();
  const response = await fetch(`${resolveApiBaseUrl()}/api/subscription/create-checkout-session`, {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify(plan != null ? { plan } : {}),
  });

  if (!response.ok) {
    if (response.status === 401) throw new Error('Unauthorized');
    if (response.status === 503) {
      const data = await response.json().catch(() => ({}));
      throw new Error(data?.error || 'Payment provider not configured');
    }
    const data = await response.json().catch(() => ({}));
    throw new Error(data?.error || 'Failed to create checkout session');
  }

  const data = await response.json();
  const url = data.checkoutUrl || data.url;
  if (!url || typeof url !== 'string') throw new Error('Invalid checkout URL received');
  if (!isStripeCheckoutUrl(url)) throw new Error('Invalid checkout URL received');
  return url;
}

export async function createPortalSession(returnUrl?: string): Promise<string> {
  const headers = getAuthHeaders();
  const response = await fetch(`${resolveApiBaseUrl()}/api/subscription/create-portal-session`, {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify(returnUrl != null ? { returnUrl } : {}),
  });

  if (!response.ok) {
    if (response.status === 503) {
      const data = await response.json().catch(() => ({}));
      throw new Error(data?.error || 'Payment provider not configured');
    }
    if (response.status === 404) throw new Error('Not found');
    if (response.status === 403) throw new Error('Not allowed');
    const data = await response.json().catch(() => ({}));
    throw new Error(data?.error || 'Failed to open billing portal');
  }

  const data = await response.json();
  const url = data.portalUrl || data.url;
  if (!url || typeof url !== 'string') throw new Error('Invalid portal URL');
  if (!isStripePortalUrl(url)) throw new Error('Invalid portal URL');
  return url;
}

export async function syncSubscriptionFromCheckoutSession(
  sessionId: string
): Promise<SyncFromSessionResult> {
  try {
    const headers = getAuthHeaders();
    const response = await fetch(`${resolveApiBaseUrl()}/api/subscription/sync-from-session`, {
      method: 'POST',
      credentials: 'include',
      headers: { ...headers, 'Content-Type': 'application/json' },
      body: JSON.stringify({ session_id: sessionId }),
    });
    const data = await response.json().catch(() => ({}));
    if (response.ok) {
      return { ok: true, data: data as SubscriptionStatusApi & { synced?: boolean } };
    }
    const message = typeof data?.error === 'string' ? data.error : 'Activation failed. Try Refresh or contact support.';
    return { ok: false, error: message };
  } catch {
    return { ok: false, error: 'Network error. Check your connection and try Refresh.' };
  }
}
