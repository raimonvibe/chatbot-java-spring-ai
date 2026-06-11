'use client';

import { useEffect, useState, useRef, Suspense } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { motion } from 'framer-motion';
import { Book } from 'lucide-react';
import { getApiBaseUrl, getAllChatbots, isApiError } from '@/lib/api';

const API_BASE_URL = getApiBaseUrl();

function safeRedirectPath(raw: string | null): string | null {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//')) return null;
  return raw;
}

function AuthCallbackContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const [status, setStatus] = useState<'processing' | 'success' | 'error'>('processing');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const hasProcessed = useRef(false);
  // Track redirect timers so they don't fire after unmount (stale navigation)
  const timersRef = useRef<ReturnType<typeof setTimeout>[]>([]);

  useEffect(() => {
    const timers = timersRef.current;
    return () => {
      timers.forEach(clearTimeout);
    };
  }, []);

  useEffect(() => {
    // Prevent multiple executions (React Strict Mode, re-renders, etc.)
    if (hasProcessed.current) {
      return;
    }
    hasProcessed.current = true;

    const redirectAfterDelay = (path: string) => {
      timersRef.current.push(setTimeout(() => {
        router.push(path);
      }, 3000));
    };

    const code = searchParams.get('code');
    const state = searchParams.get('state');
    const error = searchParams.get('error');
    const errorDescription = searchParams.get('error_description');

    if (error) {
      const message = errorDescription || error || 'Authentication failed';
      setStatus('error');
      setErrorMessage(message);
      redirectAfterDelay(`/login?error=${encodeURIComponent(message)}`);
      return;
    }

    if (!code) {
      setStatus('error');
      setErrorMessage('No authorization code received');
      redirectAfterDelay('/login?error=no_code');
      return;
    }

    // OAuth CSRF protection: state is stored in an HttpOnly cookie at login; verify server-side.
    const verifyState = async (): Promise<boolean> => {
      try {
        const verifyRes = await fetch('/api/auth/oauth-state/verify', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ state: state ?? '' }),
          credentials: 'same-origin',
        });
        return verifyRes.ok;
      } catch {
        return false;
      }
    };

    // Send authorization code to backend for token exchange
    const exchangeCode = async () => {
      try {
        const response = await fetch(`${API_BASE_URL}/api/auth/oauth2/callback`, {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({
            code,
            redirect_uri: `${window.location.origin}/auth/callback`,
          }),
          credentials: 'include', // Important for cookies/sessions
        });

        if (!response.ok) {
          const errorData = await response.json().catch(() => ({}));
          // Handle specific OAuth errors
          if (errorData.error === 'Authorization code expired' || errorData.message?.includes('expired')) {
            throw new Error('Your login session expired. Please try logging in again.');
          }
          throw new Error(errorData.message || errorData.error || `HTTP ${response.status}`);
        }

        const data = await response.json();

        // HttpOnly cookie may carry the session when the API omits token (production hardening).
        if (data.user) {
          localStorage.setItem('user', JSON.stringify(data.user));

          // Honor the destination the user originally tried to reach (?redirect= on /login).
          let storedRedirect: string | null = null;
          try {
            storedRedirect = safeRedirectPath(sessionStorage.getItem('postLoginRedirect'));
            sessionStorage.removeItem('postLoginRedirect');
          } catch {
            // sessionStorage unavailable — fall through to default routing
          }
          if (storedRedirect) {
            router.replace(storedRedirect);
            return;
          }

          // Route by chatbot count so new users skip dashboard empty-state flash (Option C).
          try {
            const chatbots = await getAllChatbots();
            router.replace(chatbots.length > 0 ? '/dashboard' : '/onboarding');
          } catch (listErr) {
            if (isApiError(listErr) && listErr.status === 401) {
              router.replace('/login?error=session_expired');
              return;
            }
            console.warn('Post-login chatbot list failed; sending to dashboard:', listErr);
            router.replace('/dashboard');
          }
        } else {
          throw new Error('No session data received from server');
        }
      } catch (err) {
        console.error('OAuth callback error:', err);
        const message = err instanceof Error ? err.message : 'Authentication failed';
        setStatus('error');
        setErrorMessage(message);
        redirectAfterDelay(`/login?error=${encodeURIComponent(message)}`);
      }
    };

    const runCallback = async () => {
      const stateOk = await verifyState();
      if (!stateOk) {
        setStatus('error');
        setErrorMessage('Invalid login state. Please try signing in again.');
        redirectAfterDelay('/login?error=invalid_oauth_state');
        return;
      }
      await exchangeCode();
    };

    void runCallback();
  }, [searchParams, router]);  // Removed errorMessage to prevent re-execution loop

  return (
    <main className="relative min-h-screen overflow-hidden flex items-center justify-center">
      <div className="relative z-10 w-full max-w-md p-8">
        <motion.div
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
          className="bg-brown-50/90 backdrop-blur-sm rounded-2xl shadow-2xl p-8 border border-brown-200"
        >
          <div className="flex items-center justify-center gap-3 mb-6">
            <Book className="w-12 h-12 text-brown-700" strokeWidth={1.5} />
            <h1 className="text-4xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-brown-700 via-brown-600 to-gold-700">
              Prayer-Chat
            </h1>
          </div>

          {status === 'processing' && (
            <div className="text-center">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-brown-700 mx-auto mb-4"></div>
              <h2 className="text-2xl font-semibold text-brown-800 mb-2">
                Completing Login
              </h2>
              <p className="text-brown-600">
                Please wait while we complete your authentication...
              </p>
            </div>
          )}

          {status === 'error' && (
            <div className="text-center">
              <div className="text-red-600 text-5xl mb-4">✗</div>
              <h2 className="text-2xl font-semibold text-brown-800 mb-2">
                Authentication Failed
              </h2>
              <p className="text-brown-600 mb-4">
                {errorMessage || 'An error occurred during authentication'}
              </p>
              <p className="text-sm text-brown-500">
                Redirecting to login page...
              </p>
            </div>
          )}
        </motion.div>
      </div>
    </main>
  );
}

export default function AuthCallback() {
  return (
    <Suspense fallback={
      <main className="relative min-h-screen overflow-hidden flex items-center justify-center">
        <div className="relative z-10 w-full max-w-md p-8">
          <motion.div
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.6 }}
            className="bg-brown-50/90 backdrop-blur-sm rounded-2xl shadow-2xl p-8 border border-brown-200"
          >
            <div className="flex items-center justify-center gap-3 mb-6">
              <Book className="w-12 h-12 text-brown-700" strokeWidth={1.5} />
              <h1 className="text-4xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-brown-700 via-brown-600 to-gold-700">
                Prayer-Chat
              </h1>
            </div>
            <div className="text-center">
              <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-brown-700 mx-auto mb-4"></div>
              <h2 className="text-2xl font-semibold text-brown-800 mb-2">
                Loading...
              </h2>
            </div>
          </motion.div>
        </div>
      </main>
    }>
      <AuthCallbackContent />
    </Suspense>
  );
}

