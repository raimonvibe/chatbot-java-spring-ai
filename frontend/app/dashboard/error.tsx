'use client';

import ErrorBanner from '@/components/ui/ErrorBanner';

export default function DashboardError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <main className="min-h-screen flex items-center justify-center p-6">
      <div className="max-w-md w-full space-y-4">
        <ErrorBanner
          message={error.message || 'Something went wrong loading the dashboard.'}
          action={
            <button
              type="button"
              onClick={reset}
              className="shrink-0 px-4 py-2 rounded-lg bg-brown-700 text-white text-sm font-medium hover:bg-brown-800"
            >
              Try again
            </button>
          }
        />
      </div>
    </main>
  );
}
