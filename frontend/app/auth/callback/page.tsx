'use client';

import { useEffect, useState, useRef, Suspense } from 'react';
import { useSearchParams, useRouter } from 'next/navigation';
import { motion } from 'framer-motion';
import { Book } from 'lucide-react';

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

function AuthCallbackContent() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const [status, setStatus] = useState<'processing' | 'success' | 'error'>('processing');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const hasProcessed = useRef(false);

  useEffect(() => {
    // Prevent multiple executions (React Strict Mode, re-renders, etc.)
    if (hasProcessed.current) {
      return;
    }
    hasProcessed.current = true;
    const code = searchParams.get('code');
    const error = searchParams.get('error');
    const errorDescription = searchParams.get('error_description');

    if (error) {
      setStatus('error');
      setErrorMessage(errorDescription || error || 'Authentication failed');
      // Redirect to login page after 3 seconds
      setTimeout(() => {
        router.push(`/login?error=${encodeURIComponent(errorMessage || error)}`);
      }, 3000);
      return;
    }

    if (!code) {
      setStatus('error');
      setErrorMessage('No authorization code received');
      setTimeout(() => {
        router.push('/login?error=no_code');
      }, 3000);
      return;
    }

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
        }
        if (data.token) {
          localStorage.setItem('authToken', data.token);
        } else {
          localStorage.removeItem('authToken');
        }

        if (data.token || data.user) {
          setStatus('success');
          setTimeout(() => {
            router.push('/dashboard');
          }, 1000);
        } else {
          throw new Error('No session data received from server');
        }
      } catch (err) {
        console.error('OAuth callback error:', err);
        setStatus('error');
        setErrorMessage(err instanceof Error ? err.message : 'Authentication failed');
        
        // Redirect to login page after 3 seconds
        setTimeout(() => {
          router.push(`/login?error=${encodeURIComponent(errorMessage || 'authentication_failed')}`);
        }, 3000);
      }
    };

    exchangeCode();
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

          {status === 'success' && (
            <div className="text-center">
              <div className="text-green-600 text-5xl mb-4">✓</div>
              <h2 className="text-2xl font-semibold text-brown-800 mb-2">
                Login Successful!
              </h2>
              <p className="text-brown-600">
                Redirecting to dashboard...
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

