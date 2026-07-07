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

export function sanitizeErrorMessage(value: unknown): string {
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
