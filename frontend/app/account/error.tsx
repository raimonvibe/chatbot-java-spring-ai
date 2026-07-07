'use client';

import ErrorBanner from '@/components/ui/ErrorBanner';

export default function AccountError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <main className="min-h-screen flex items-center justify-center text-brown-50 p-6">
      <div className="max-w-md w-full">
        <ErrorBanner
          message={error.message || 'Could not load your account.'}
          action={
            <button
              type="button"
              onClick={reset}
              className="px-4 py-2 rounded-xl bg-gold-600 hover:bg-gold-500 text-brown-950 text-sm font-semibold"
            >
              Try again
            </button>
          }
        />
      </div>
    </main>
  );
}
