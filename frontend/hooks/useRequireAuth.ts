'use client';

import { useCallback, useEffect, useState } from 'react';
import { usePathname, useRouter } from 'next/navigation';
import { checkAuth, type AuthUser } from '@/lib/api';

export type { AuthUser };

export interface UseRequireAuthResult {
  authenticated: boolean;
  loading: boolean;
  networkError: boolean;
  user: AuthUser | null;
  refresh: () => Promise<void>;
}

/**
 * Shared auth guard for protected client pages.
 * Redirects unauthenticated users to /login with a return URL.
 */
export function useRequireAuth(options?: { redirect?: boolean }): UseRequireAuthResult {
  const router = useRouter();
  const pathname = usePathname();
  const shouldRedirect = options?.redirect !== false;

  const [authenticated, setAuthenticated] = useState(false);
  const [loading, setLoading] = useState(true);
  const [networkError, setNetworkError] = useState(false);
  const [user, setUser] = useState<AuthUser | null>(null);

  const refresh = useCallback(async () => {
    setLoading(true);
    try {
      const auth = await checkAuth();
      setAuthenticated(!!auth.authenticated);
      setNetworkError(!!auth.networkError);
      setUser(auth.user ?? null);
    } catch {
      setAuthenticated(false);
      setNetworkError(true);
      setUser(null);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  useEffect(() => {
    if (!shouldRedirect || loading || authenticated || networkError) return;
    router.replace(`/login?redirect=${encodeURIComponent(pathname)}`);
  }, [shouldRedirect, loading, authenticated, networkError, router, pathname]);

  return { authenticated, loading, networkError, user, refresh };
}
