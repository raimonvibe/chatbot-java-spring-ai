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

export const resolveApiBaseUrl = getApiBaseUrl;

/**
 * Cookie-first auth client headers.
 * Authentication is carried by HttpOnly cookies via credentials: 'include',
 * so we intentionally do not read/store bearer tokens in localStorage.
 */
export function getAuthHeaders(): HeadersInit {
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
