import { getAuthHeaders, resolveApiBaseUrl } from './api-client';
import type { AuthUser } from './api-types';

export async function checkAuth(): Promise<{ authenticated: boolean; user?: AuthUser; networkError?: boolean }> {
  try {
    const headers = getAuthHeaders();
    const response = await fetch(`${resolveApiBaseUrl()}/api/auth/me`, {
      method: 'GET',
      credentials: 'include',
      headers,
    });

    if (response.ok) {
      const user = await response.json();
      return { authenticated: true, user };
    }
    if (response.status === 401 || response.status === 403) {
      return { authenticated: false };
    }
    return { authenticated: false, networkError: true };
  } catch {
    return { authenticated: false, networkError: true };
  }
}

export async function logout(): Promise<{ message: string; googleLogoutUrl?: string }> {
  const headers = getAuthHeaders();
  const response = await fetch(`${resolveApiBaseUrl()}/api/auth/logout`, {
    method: 'POST',
    credentials: 'include',
    headers,
  });

  if (!response.ok) {
    throw new Error('Failed to logout');
  }

  const result = await response.json();

  document.cookie.split(';').forEach((c) => {
    const eqPos = c.indexOf('=');
    const name = eqPos > -1 ? c.substr(0, eqPos).trim() : c.trim();
    document.cookie = `${name}=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/`;
    document.cookie = `${name}=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/;domain=${window.location.hostname}`;
    document.cookie = `${name}=;expires=Thu, 01 Jan 1970 00:00:00 GMT;path=/;domain=.${window.location.hostname}`;
  });

  localStorage.clear();
  sessionStorage.clear();

  return result;
}
