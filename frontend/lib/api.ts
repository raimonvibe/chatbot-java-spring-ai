/** Error thrown by API calls that may include status (e.g. 402) and upgradeRequired for paywall handling */
export interface ApiError extends Error {
  status?: number;
  upgradeRequired?: boolean;
  /** Set when API returns estimatedPages + maxPages (site exceeds per-scan page cap). */
  websiteTooLarge?: boolean;
  estimatedPages?: number;
  maxPages?: number;
}

/** Maximum length for user-facing error messages to avoid UI abuse or huge strings */
const MAX_ERROR_MESSAGE_LENGTH = 500;

/** Allowed diagnostic scopes for client logging (prevents log injection / bogus labels). */
const SAFE_LOG_SCOPE = /^[a-z][a-z0-9_.-]{0,79}$/i;

/**
 * Strips C0/C1 controls, DEL, and Unicode bidi embedding markers from text shown to users or summarized in logs
 * (UI/log confusion, RTL spoofing in reflected error strings). React text nodes still benefit from single-line cleanup.
 */
function stripUnsafeDisplayChars(s: string): string {
  return s
    .replace(/[\u0000-\u001f\u007f\u202a-\u202e\u2066-\u2069]+/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

function sanitizeErrorMessage(value: unknown): string {
  if (value == null) return '';
  const s = typeof value === 'string' ? value : String(value);
  if (s === '[object Object]') return '';
  const clipped = s.slice(0, MAX_ERROR_MESSAGE_LENGTH).trim();
  return stripUnsafeDisplayChars(clipped);
}

function sanitizeErrorTypeName(name: string): string {
  if (/^[A-Za-z][A-Za-z0-9]*$/.test(name) && name.length <= 40) return name;
  return 'Error';
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof Error && 'status' in error;
}

/** Returns a safe, truncated string for display; never exposes stack or object dump */
export function getSafeErrorMessage(error: unknown, fallback: string): string {
  if (error instanceof Error && typeof error.message === 'string' && error.message) {
    const msg = sanitizeErrorMessage(error.message);
    return msg || fallback;
  }
  return fallback;
}

/**
 * True when the browser failed before an HTTP response (offline, tab backgrounded,
 * Chrome ERR_NETWORK_CHANGED after Wi‑Fi/VPN switch, extensions blocking the request, CORS at network layer).
 * Does not detect HTTP 5xx (those are not "network" in this sense).
 */
export function isLikelyNetworkError(error: unknown): boolean {
  if (!(error instanceof Error)) return false;
  const m = (error.message || '').trim();
  if (error.name === 'AbortError') return true;
  if (m === 'Failed to fetch') return true;
  if (m === 'NetworkError when attempting to fetch resource.') return true;
  if (m.includes('Load failed')) return true;
  if (/failed to fetch/i.test(m)) return true;
  return false;
}

/**
 * User-facing message for API calls using fetch: explains transient network loss clearly
 * instead of the vague "Failed to fetch" from the browser.
 */
export function getUserFacingFetchError(error: unknown, fallback: string): string {
  if (isLikelyNetworkError(error)) {
    return 'Your connection was interrupted (for example, Wi-Fi or VPN changed). Please try again.';
  }
  return getSafeErrorMessage(error, fallback);
}

/**
 * Logs a client failure: full details in development, one-line summary in production
 * (avoids huge stacks in hosted consoles; never logs tokens).
 */
export function logClientIssue(scope: string, error: unknown): void {
  const safeScope = SAFE_LOG_SCOPE.test(scope) ? scope : 'client';
  const isDev = process.env.NODE_ENV === 'development';
  if (isDev) {
    console.error(`[Prayer-Chat:${safeScope}]`, error);
    return;
  }
  const net = isLikelyNetworkError(error);
  const label =
    error instanceof Error
      ? `${sanitizeErrorTypeName(error.name)}: ${sanitizeErrorMessage(error.message).slice(0, 200)}`
      : 'non-Error thrown';
  console.warn(`[Prayer-Chat:${safeScope}]`, net ? 'network_or_cors' : 'error', label);
}

export interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: number;
}

export interface ChatResponse {
  message: string;
  sessionId: string;
  timestamp: number;
  chatbotId: number;
}

export interface Chatbot {
  id: number;
  name: string;
  description: string;
  primaryLanguage: string;
  supportedLanguages: string[];
  brandingConfig: string;
  websiteUrl?: string;
  christianMessagingEnabled?: boolean;
  jesusTeachingsEnabled?: boolean;
  bibleVerse?: string;
  /** Avatar image id: "1".."12" or null/empty for no avatar. Server-validated. */
  avatarId?: string | null;
}

/** Allowed avatar ids for picker and display (must match backend EmbedSecurity.ALLOWED_AVATAR_IDS). */
export const AVATAR_IDS = [
  '1',
  '2',
  '3',
  '4',
  '5',
  '6',
  '7',
  '8',
  '9',
  '10',
  '11',
  '12',
] as const;
export type AvatarId = (typeof AVATAR_IDS)[number];

export interface JesusTeachingPreview {
  reference: string;
  text: string;
  similarity: string;
}

export interface JesusTeachingsPreviewResponse {
  chatbotId: string;
  websiteUrl: string;
  topTeachings: JesusTeachingPreview[];
  totalJesusVerses: number;
}

export interface VerseMatch {
  id: number;
  reference: string;
  book: string;
  chapter: number;
  verse: number;
  text: string;
  translation: string;
  similarity: number;
  similarityPercentage: number;
}

export interface ChristianContentAnalysis {
  chatbotId: number;
  websiteUrl: string;
  themes?: string[];
  relevantVerses: VerseMatch[];
  averageSimilarity: number;
  totalVersesAnalyzed: number;
  versesAboveThreshold: number;
}

/**
 * Backend origin for API fetches.
 * - Preferred in all environments: NEXT_PUBLIC_API_URL (explicit backend origin).
 * - Browser fallback for local dev: localhost:8081 when on localhost.
 * - Browser fallback for production when env is missing: window.location.origin.
 * - SSR fallback: localhost:8081.
 *
 * NOTE:
 * We prefer explicit backend origin because OAuth callback auth cookies (PC_AUTH) must be
 * set/read consistently by the backend domain; relying on proxy rewrites can drop/alter cookie behavior.
 */
export function getApiBaseUrl(): string {
  const fromEnv = process.env.NEXT_PUBLIC_API_URL?.trim()?.replace(/\/$/, '');
  if (fromEnv) {
    return fromEnv;
  }

  if (typeof window !== 'undefined') {
    const hostname = window.location.hostname;
    if (hostname === 'localhost' || hostname === '127.0.0.1') {
      return 'http://localhost:8081';
    }
    return window.location.origin;
  }
  return 'http://localhost:8081';
}

const API_BASE_URL = getApiBaseUrl();

/**
 * Cookie-first auth client headers.
 * Authentication is carried by HttpOnly cookies via credentials: 'include',
 * so we intentionally do not read/store bearer tokens in localStorage.
 */
function getAuthHeaders(): HeadersInit {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
  };
  const csrfToken = getCookieValue('XSRF-TOKEN');
  if (csrfToken) {
    headers['X-XSRF-TOKEN'] = csrfToken;
  }
  return headers;
}

function getCookieValue(name: string): string | null {
  if (typeof document === 'undefined') return null;
  const cookie = document.cookie
    .split(';')
    .map((part) => part.trim())
    .find((part) => part.startsWith(`${name}=`));
  if (!cookie) return null;
  return decodeURIComponent(cookie.substring(name.length + 1));
}

// Analyze Christian content for a chatbot
export async function analyzeChristianContent(
  chatbotId: number,
  maxVerses: number = 20,
  similarityThreshold: number = 0.5
): Promise<ChristianContentAnalysis> {
  const headers = getAuthHeaders();
  const response = await fetch(
    `${API_BASE_URL}/api/chatbots/${chatbotId}/analyze-christian-content?maxVerses=${maxVerses}&similarityThreshold=${similarityThreshold}`,
    {
      method: 'POST',
      credentials: 'include',
      headers,
    }
  );

  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    const err = new Error(errorData.error || 'Failed to analyze Christian content') as Error & { code?: string };
    err.code = errorData.code;
    throw err;
  }

  return response.json();
}

// Check if user is authenticated (server-validated; only trust this for UI state)
export async function checkAuth(): Promise<{ authenticated: boolean; user?: any }> {
  try {
    const headers = getAuthHeaders();
    const response = await fetch(`${API_BASE_URL}/api/auth/me`, {
      method: 'GET',
      credentials: 'include',
      headers,
    });

    if (response.ok) {
      const user = await response.json();
      return { authenticated: true, user };
    }
    return { authenticated: false };
  } catch (error) {
    return { authenticated: false };
  }
}

export async function sendMessage(
  chatbotId: number,
  message: string,
  sessionId?: string,
  language: string = 'en'
): Promise<ChatResponse> {
  // credentials: 'include' so PC_AUTH (API host) is sent from the dashboard (embed uses /api/chat/embed/* + omit in widget JS).
  const headers = getAuthHeaders();
  const response = await fetch(`${API_BASE_URL}/api/chat/${chatbotId}`, {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify({
      message,
      sessionId,
      language,
    }),
  });

  if (response.status === 401) {
    const err = new Error(
      'Your session expired. Please sign in again to use chat preview.'
    ) as ApiError;
    err.status = 401;
    throw err;
  }
  if (!response.ok) {
    const errorData = await response.json().catch(() => ({}));
    const raw = errorData?.error != null ? errorData.error : `HTTP ${response.status}: ${response.statusText}`;
    const errorMessage = sanitizeErrorMessage(raw) || 'Failed to send message. Please try again.';
    throw new Error(errorMessage);
  }

  return response.json();
}

export async function getChatbot(chatbotId: number): Promise<Chatbot> {
  const headers = getAuthHeaders();
  const response = await fetch(`${API_BASE_URL}/api/chatbots/${chatbotId}`, {
    credentials: 'include',
    headers,
    cache: 'no-store',
  });

  if (!response.ok) {
    throw new Error('Failed to fetch chatbot');
  }

  return response.json();
}

/**
 * Update chatbot (PATCH). Pass partial Chatbot; only provided fields are updated.
 */
export async function updateChatbot(chatbotId: number, updates: Partial<Chatbot>): Promise<Chatbot> {
  const headers = getAuthHeaders();
  const response = await fetch(`${API_BASE_URL}/api/chatbots/${chatbotId}`, {
    method: 'PATCH',
    credentials: 'include',
    headers,
    body: JSON.stringify(updates),
  });

  if (!response.ok) {
    if (response.status === 404) throw new Error('Chatbot not found');
    if (response.status === 403) throw new Error('You do not have permission to update this chatbot');
    throw new Error('Failed to update chatbot');
  }

  return response.json();
}

/**
 * Delete a chatbot (owner only). Server keeps per-user website scan audit rows, so quotas do not reset.
 */
export async function deleteChatbot(chatbotId: number): Promise<void> {
  const headers = getAuthHeaders();
  const response = await fetch(`${API_BASE_URL}/api/chatbots/${chatbotId}`, {
    method: 'DELETE',
    credentials: 'include',
    headers,
  });
  if (response.status === 204) {
    return;
  }
  if (response.status === 401) {
    const err = new Error('Please sign in again to continue.') as ApiError;
    err.status = 401;
    throw err;
  }
  if (response.status === 404) {
    throw new Error('Chatbot not found');
  }
  if (response.status === 403) {
    throw new Error('You do not have permission to delete this chatbot');
  }
  const data = await response.json().catch(() => ({}));
  throw new Error((data as { error?: string }).error || 'Failed to delete chatbot');
}

/** Allow only Stripe checkout redirect URLs (security: prevent open redirect from API response). */
function isStripeCheckoutUrl(url: string): boolean {
  try {
    const u = new URL(url);
    return u.protocol === 'https:' && u.hostname === 'checkout.stripe.com';
  } catch {
    return false;
  }
}

/** Allow only Stripe billing portal redirect URLs. */
function isStripePortalUrl(url: string): boolean {
  try {
    const u = new URL(url);
    return u.protocol === 'https:' && u.hostname === 'billing.stripe.com';
  } catch {
    return false;
  }
}

/**
 * Create a Stripe checkout session for subscription.
 * @param plan Optional plan: BASIC, PRO, or ENTERPRISE. If omitted, backend uses default price.
 * @returns Checkout URL to redirect the user to.
 */
export async function createCheckoutSession(plan?: 'BASIC' | 'PRO' | 'ENTERPRISE'): Promise<string> {
  const headers = getAuthHeaders();
  const response = await fetch(`${API_BASE_URL}/api/subscription/create-checkout-session`, {
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

/**
 * Create a Stripe Customer Billing Portal session (manage subscription, payment method, cancel).
 * Returns URL to redirect the user to.
 */
export async function createPortalSession(returnUrl?: string): Promise<string> {
  const headers = getAuthHeaders();
  const response = await fetch(`${API_BASE_URL}/api/subscription/create-portal-session`, {
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

/**
 * Preview Jesus's teachings relevant to the chatbot's website (for "What Jesus Would Say" feature).
 */
export async function previewJesusTeachings(
  chatbotId: number,
  maxTeachings: number = 5
): Promise<JesusTeachingsPreviewResponse> {
  const headers = getAuthHeaders();
  const response = await fetch(
    `${API_BASE_URL}/api/chatbots/${chatbotId}/preview-jesus-teachings?maxTeachings=${maxTeachings}`,
    {
      method: 'POST',
      credentials: 'include',
      headers,
    }
  );

  if (!response.ok) {
    if (response.status === 404) throw new Error('Chatbot not found');
    if (response.status === 403) throw new Error('You do not have permission to preview teachings');
    throw new Error('Failed to preview Jesus teachings');
  }

  return response.json();
}

export async function getQuickReplies(chatbotId: number): Promise<string[]> {
  try {
    const headers = getAuthHeaders();
    const response = await fetch(`${API_BASE_URL}/api/chatbots/${chatbotId}/quick-replies`, {
      credentials: 'include',
      headers,
    });

    if (!response.ok) {
      return [];
    }

    const data = await response.json();
    return Array.isArray(data) ? data : [];
  } catch (error) {
    // Return empty array on network errors
    return [];
  }
}

export async function getAllChatbots(): Promise<Chatbot[]> {
  const headers = getAuthHeaders();
  const response = await fetch(`${API_BASE_URL}/api/chatbots`, {
    credentials: 'include',
    headers,
    cache: 'no-store',
  });

  if (!response.ok) {
    const error = new Error(`Failed to fetch chatbots: ${response.status}`);
    (error as any).status = response.status;
    throw error;
  }

  return response.json();
}

/**
 * Simplified onboarding - create chatbot from website URL only
 * Auto-generates name and pre-configures Christian values
 */
export async function createChatbotFromUrl(websiteUrl: string): Promise<Chatbot> {
  const headers = getAuthHeaders();
  const response = await fetch(`${API_BASE_URL}/api/chatbots/onboarding`, {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify({ websiteUrl }),
  });

  if (!response.ok) {
    const errorData = (await response.json().catch(() => ({}))) as Record<string, unknown>;
    const errMsg =
      typeof errorData.error === 'string' && errorData.error.trim()
        ? sanitizeErrorMessage(errorData.error)
        : `HTTP ${response.status}: ${response.statusText}`;
    const error = new Error(errMsg || `HTTP ${response.status}`) as ApiError;
    error.status = response.status;
    if (typeof errorData.upgradeRequired === 'boolean') {
      error.upgradeRequired = errorData.upgradeRequired;
    }
    if (typeof errorData.estimatedPages === 'number' && typeof errorData.maxPages === 'number') {
      error.websiteTooLarge = true;
      error.estimatedPages = errorData.estimatedPages;
      error.maxPages = errorData.maxPages;
    }
    if (response.status === 402) {
      error.upgradeRequired = true;
    }
    throw error;
  }

  return response.json();
}

export async function createChatbot(data: {
  name: string;
  description: string;
  websiteUrl: string;
  primaryLanguage?: string;
}): Promise<Chatbot> {
  const headers = getAuthHeaders();
  const response = await fetch(`${API_BASE_URL}/api/chatbots`, {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify(data),
  });

  if (!response.ok) {
    // Handle 402 Payment Required (website size limit)
    if (response.status === 402) {
      const errorData = await response.json().catch(() => ({ error: 'Upgrade required' }));
      const error = new Error(errorData.error || 'Website too large for preview mode. Upgrade to continue.');
      (error as any).status = 402;
      (error as any).upgradeRequired = true;
      (error as any).estimatedPages = errorData.estimatedPages;
      (error as any).maxPages = errorData.maxPages;
      throw error;
    }
    
    const errorData = await response.json().catch(() => ({ error: 'Unknown error' }));
    const errorMessage = errorData.error || `HTTP ${response.status}: ${response.statusText}`;
    throw new Error(errorMessage);
  }

  return response.json();
}

export interface AnalysisStatus {
  ready: boolean;
  pagesIndexed?: number;
}

const ANALYSIS_STATUS_FETCH_TIMEOUT_MS = 10000; // 10s per request so one slow response doesn't hang the UI

export async function getAnalysisStatus(chatbotId: number): Promise<AnalysisStatus> {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), ANALYSIS_STATUS_FETCH_TIMEOUT_MS);
  try {
    const headers = getAuthHeaders();
    const response = await fetch(`${API_BASE_URL}/api/chatbots/${chatbotId}/analysis-status`, {
      credentials: 'include',
      headers,
      signal: controller.signal,
    });
    if (!response.ok) {
      const err = new Error('Failed to get analysis status') as ApiError;
      err.status = response.status;
      throw err;
    }
    const data = await response.json();
    return { ready: !!data.ready, pagesIndexed: data.pagesIndexed ?? 0 };
  } finally {
    clearTimeout(timeoutId);
  }
}

/** Poll until website analysis is ready or timeout. Keeps loading screen until chatbot can answer about the site. */
export async function pollUntilAnalysisReady(
  chatbotId: number,
  options: { intervalMs?: number; timeoutMs?: number } = {}
): Promise<AnalysisStatus> {
  const { intervalMs = 1000, timeoutMs = 120000 } = options; // 1s poll — COUNT endpoint is cheap; 2min max
  const start = Date.now();
  let lastStatus: AnalysisStatus = { ready: false, pagesIndexed: 0 };
  while (Date.now() - start < timeoutMs) {
    try {
      const status = await getAnalysisStatus(chatbotId);
      lastStatus = status;
      if (status.ready) return status;
    } catch (e) {
      // Auth / not-found: retrying won't help — exit so the UI doesn't spin for the full timeout
      if (isApiError(e)) {
        const s = e.status;
        if (s === 401 || s === 403 || s === 404) {
          console.warn('Analysis status poll stopped:', s);
          return lastStatus;
        }
      }
      // Transient network / abort / 5xx: retry
      console.warn('Analysis status poll failed, retrying:', e);
    }
    await new Promise((r) => setTimeout(r, intervalMs));
  }
  return lastStatus; // proceed after timeout so UI doesn't stall forever
}

export async function analyzeWebsite(chatbotId: number, websiteUrl: string): Promise<any> {
  const headers = getAuthHeaders();
  const response = await fetch(`${API_BASE_URL}/api/chatbots/${chatbotId}/analyze`, {
    method: 'POST',
    credentials: 'include',
    headers,
    body: JSON.stringify({ websiteUrl }),
  });

  if (!response.ok) {
    let errorData: { error?: unknown } = {};
    try {
      if (typeof response.json === 'function') {
        errorData = await response.json();
      }
    } catch {
      // Ignore parse errors; use fallback message
    }
    const message = sanitizeErrorMessage(errorData?.error) || 'Failed to analyze website';
    const err = new Error(message) as ApiError;
    if (response.status === 402) {
      err.status = 402;
      err.upgradeRequired = true;
    }
    throw err;
  }

  return response.json();
}

export interface SubscriptionStatus {
  isPreviewMode: boolean;
  canAccessIntegrationScript: boolean;
  maxChatbots: number;
  currentChatbotCount: number;
  plan?: string;
  /** Mirrors backend when billing integration is off (Stripe checkout/portal hidden). */
  billingEnabled?: boolean;
  paymentActionsAvailable?: boolean;
  /** From GET /api/subscription/status — min(monthly headroom, daily headroom). */
  websiteScansRemaining?: number;
  websiteScansMonthlyQuota?: number;
  websiteScansUsedThisMonth?: number;
  websiteScansDailyLimit?: number;
  websiteScansUsedRollingDay?: number;
}

/** Response from GET /api/subscription/status */
export interface SubscriptionStatusApi {
  hasSubscription: boolean;
  status: string;
  plan: string;
  isActive: boolean;
  canUseChatbot: boolean;
  currentPeriodEnd?: string;
  canceledAt?: string;
  billingEnabled?: boolean;
  paymentActionsAvailable?: boolean;
  websiteScansMonthlyQuota?: number;
  websiteScansUsedThisMonth?: number;
  websiteScansDailyLimit?: number;
  websiteScansUsedRollingDay?: number;
  websiteScansRemaining?: number;
}

/**
 * Copy for delete-chatbot confirmation: limits + how many scans the account can still run (when API provides it).
 */
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

/** Maps scan quota fields from subscription status API into dashboard state. */
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

/** Public GET /api/plans/limits (no auth). */
export interface PublicPlanLimitsResponse {
  description?: string;
  billingEnabled?: boolean;
  maxPagesPerScanOffered?: number;
  websiteScanPolicySummary?: string;
  plans?: Record<string, { maxPagesPerScan: number; messagesPerDay: number; monthlyScanQuota: number; maxChatbots?: number }>;
  standardPageTiers?: Record<string, number>;
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
    const response = await fetch(`${API_BASE_URL}/api/subscription/status`, {
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

/** Result of sync-from-session: success with data, or failure with error message for UI */
export type SyncFromSessionResult =
  | { ok: true; data: SubscriptionStatusApi & { synced?: boolean } }
  | { ok: false; error: string };

/**
 * Sync subscription from Stripe checkout session (e.g. after payment redirect when webhook hasn't run yet).
 * Backend validates session belongs to current user. Returns result with data or error message for UI.
 */
export async function syncSubscriptionFromCheckoutSession(
  sessionId: string
): Promise<SyncFromSessionResult> {
  try {
    const headers = getAuthHeaders();
    const response = await fetch(`${API_BASE_URL}/api/subscription/sync-from-session`, {
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

export async function getSubscriptionStatus(): Promise<SubscriptionStatus> {
  try {
    // Try to get user info which includes subscription status
    const authResult = await checkAuth();
    if (!authResult.authenticated || !authResult.user) {
      return {
        isPreviewMode: true,
        canAccessIntegrationScript: false,
        maxChatbots: 1,
        currentChatbotCount: 0,
      };
    }
    
    // Check if user has subscription by trying to access a paid feature
    // If embed endpoint returns 402, user is in preview mode
    // This is a simple heuristic - in production, you'd have a dedicated endpoint
    return {
      isPreviewMode: true, // Default to preview mode
      canAccessIntegrationScript: false,
      maxChatbots: 1,
      currentChatbotCount: 0,
    };
  } catch (error) {
    return {
      isPreviewMode: true,
      canAccessIntegrationScript: false,
      maxChatbots: 1,
      currentChatbotCount: 0,
    };
  }
}

export async function getEmbedCode(chatbotId: number): Promise<string> {
  const headers = getAuthHeaders();
  const response = await fetch(`${API_BASE_URL}/api/chatbots/${chatbotId}/embed`, {
    credentials: 'include',
    headers,
  });

  if (!response.ok) {
    if (response.status === 401) {
      const err = new Error(
        'Please sign in again to copy your embed code. Refresh the page, sign in, and try once more.'
      ) as ApiError;
      err.status = 401;
      throw err;
    }
    if (response.status === 402) {
      // Payment required - user is in preview mode
      // Try to parse as JSON first, fallback to text
      const contentType = response.headers.get('content-type');
      if (contentType && contentType.includes('application/json')) {
        const errorData = await response.json().catch(() => ({ error: 'Upgrade required' }));
        throw new Error(errorData.error || 'Upgrade to paid tier for integration script access');
      } else {
        const errorText = await response.text().catch(() => 'Upgrade required');
        throw new Error(errorText || 'Upgrade to paid tier for integration script access');
      }
    }
    // For other errors, try JSON first, then text
    const contentType = response.headers.get('content-type');
    if (contentType && contentType.includes('application/json')) {
      const errorData = await response.json().catch(() => ({ error: 'Failed to get embed code' }));
      throw new Error(errorData.error || 'Failed to get embed code');
    } else {
      const errorText = await response.text().catch(() => '');
      // Use error text if available, otherwise generic message
      throw new Error(errorText || 'Failed to get embed code');
    }
  }

  // Check content type to determine if response is JSON or plain text
  const contentType = response.headers.get('content-type');
  if (contentType && contentType.includes('application/json')) {
    const data = await response.json();
    return data.embedCode || data;
  } else {
    // Backend returns plain text (HTML/JS embed code)
    return await response.text();
  }
}

export async function logout(): Promise<{ message: string; googleLogoutUrl?: string }> {
  const headers = getAuthHeaders();
  const response = await fetch(`${API_BASE_URL}/api/auth/logout`, {
    method: 'POST',
    credentials: 'include',
    headers,
  });

  if (!response.ok) {
    throw new Error('Failed to logout');
  }

  const result = await response.json();
  
  // Clear all cookies on frontend
  document.cookie.split(";").forEach((c) => {
    const eqPos = c.indexOf("=");
    const name = eqPos > -1 ? c.substr(0, eqPos).trim() : c.trim();
    // Delete cookie for current domain and all paths
    document.cookie = `${name}=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/`;
    document.cookie = `${name}=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/;domain=${window.location.hostname}`;
    document.cookie = `${name}=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/;domain=.${window.location.hostname}`;
  });
  
  // Clear localStorage and sessionStorage
  localStorage.clear();
  sessionStorage.clear();
  
  return result;
}
