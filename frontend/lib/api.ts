/** Error thrown by API calls that may include status (e.g. 402) and upgradeRequired for paywall handling */
export interface ApiError extends Error {
  status?: number;
  upgradeRequired?: boolean;
}

/** Maximum length for user-facing error messages to avoid UI abuse or huge strings */
const MAX_ERROR_MESSAGE_LENGTH = 500;

function sanitizeErrorMessage(value: unknown): string {
  if (value == null) return '';
  const s = typeof value === 'string' ? value : String(value);
  if (s === '[object Object]') return '';
  return s.slice(0, MAX_ERROR_MESSAGE_LENGTH).trim();
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
}

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

// Auto-detect backend URL based on environment
function getApiBaseUrl(): string {
  // Use explicit environment variable if set
  if (process.env.NEXT_PUBLIC_API_URL) {
    return process.env.NEXT_PUBLIC_API_URL;
  }
  
  // Auto-detect production domain
  if (typeof window !== 'undefined') {
    const hostname = window.location.hostname;
    // Production domains - use Render backend
    if (hostname === 'prayer-chat.com' || hostname === 'www.prayer-chat.com') {
      return 'https://chatbot-java-spring-ai.onrender.com';
    }
    // Vercel preview/test deployments
    if (hostname.includes('vercel.app')) {
      return 'https://chatbot-java-spring-ai.onrender.com';
    }
  }
  
  // Default to localhost for local development
  return 'http://localhost:8081';
}

const API_BASE_URL = getApiBaseUrl();

/**
 * Helper function to get auth headers with JWT token
 * Security measures:
 * - Validates token format (JWT tokens have 3 parts separated by dots)
 * - Sanitizes token to prevent header injection
 * - Handles edge cases (null, empty, malformed tokens)
 */
function getAuthHeaders(): HeadersInit {
  const headers: HeadersInit = {
    'Content-Type': 'application/json',
  };
  
  // Get JWT token from localStorage if available
  if (typeof window !== 'undefined') {
    try {
      const token = localStorage.getItem('authToken');
      
      // Security: Validate token exists and is not empty
      if (token && token.trim().length > 0) {
        // Security: Basic JWT format validation (3 parts separated by dots)
        // JWT format: header.payload.signature
        const tokenParts = token.trim().split('.');
        
        // Security: JWT must have exactly 3 parts
        if (tokenParts.length === 3) {
          // Security: Sanitize token - remove any newlines or control characters
          // that could be used for header injection attacks
          const sanitizedToken = token.trim().replace(/[\r\n\t]/g, '');
          
          // Security: Additional validation - ensure no suspicious characters
          // JWT tokens are base64url encoded, so they should only contain
          // alphanumeric, dots, hyphens, underscores, and equals signs (for padding)
          if (/^[A-Za-z0-9._=-]+$/.test(sanitizedToken)) {
            headers['Authorization'] = `Bearer ${sanitizedToken}`;
          } else {
            // Invalid token format - log error but don't expose token
            console.warn('Invalid token format detected');
            // Clear potentially malicious token
            localStorage.removeItem('authToken');
          }
        } else {
          // Invalid JWT format - clear it
          console.warn('Malformed JWT token detected');
          localStorage.removeItem('authToken');
        }
      }
    } catch (error) {
      // Security: Handle localStorage errors gracefully (e.g., in private browsing)
      // Don't expose error details
      console.warn('Error accessing localStorage for auth token');
    }
  }
  
  return headers;
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
    // Server rejected auth (e.g. 401 expired/invalid token) — clear stale token so we don't keep sending it
    if (response.status === 401 && typeof window !== 'undefined') {
      try {
        localStorage.removeItem('authToken');
      } catch {
        // ignore localStorage errors (e.g. private browsing)
      }
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
  // credentials: 'omit' so request works from dashboard and embedded widget (backend CORS for /api/chat/** allows any origin, no credentials)
  const headers = getAuthHeaders();
  const response = await fetch(`${API_BASE_URL}/api/chat/${chatbotId}`, {
    method: 'POST',
    credentials: 'omit',
    headers,
    body: JSON.stringify({
      message,
      sessionId,
      language,
    }),
  });

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
    const response = await fetch(`${API_BASE_URL}/api/chatbots/${chatbotId}/analysis-status`, {
      credentials: 'include',
      signal: controller.signal,
    });
    if (!response.ok) {
      throw new Error('Failed to get analysis status');
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
  const { intervalMs = 2000, timeoutMs = 120000 } = options; // default 2s poll, 2 min max (avoids feeling like a long hang)
  const start = Date.now();
  let lastStatus: AnalysisStatus = { ready: false, pagesIndexed: 0 };
  while (Date.now() - start < timeoutMs) {
    try {
      const status = await getAnalysisStatus(chatbotId);
      lastStatus = status;
      if (status.ready) return status;
    } catch (e) {
      // One failed poll (network/timeout): keep last status and retry next interval instead of throwing
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
}

export async function getSubscriptionStatusFromApi(): Promise<SubscriptionStatusApi | null> {
  try {
    const headers = getAuthHeaders();
    const response = await fetch(`${API_BASE_URL}/api/subscription/status`, {
      method: 'GET',
      credentials: 'include',
      headers,
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

export async function deleteChatbot(chatbotId: number): Promise<void> {
  const headers = getAuthHeaders();
  const response = await fetch(`${API_BASE_URL}/api/chatbots/${chatbotId}`, {
    method: 'DELETE',
    credentials: 'include',
    headers,
  });

  if (!response.ok) {
    if (response.status === 404) {
      throw new Error('Chatbot not found');
    }
    if (response.status === 403) {
      throw new Error('You do not have permission to delete this chatbot');
    }
    throw new Error('Failed to delete chatbot');
  }
}

export async function deleteAllChatbots(): Promise<{ message: string; deletedCount: number }> {
  const headers = getAuthHeaders();
  const response = await fetch(`${API_BASE_URL}/api/chatbots`, {
    method: 'DELETE',
    credentials: 'include',
    headers,
  });

  if (!response.ok) {
    throw new Error('Failed to delete all chatbots');
  }

  return response.json();
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
