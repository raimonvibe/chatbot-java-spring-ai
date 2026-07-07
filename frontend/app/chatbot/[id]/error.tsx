'use client';

import Link from 'next/link';
import ErrorBanner from '@/components/ui/ErrorBanner';

export default function ChatbotPreviewError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <main className="min-h-[100dvh] flex items-center justify-center p-6">
      <div className="max-w-md w-full space-y-4 text-center">
        <ErrorBanner message={error.message || 'Could not load this chatbot preview.'} />
        <div className="flex flex-col sm:flex-row gap-3 justify-center">
          <button
            type="button"
            onClick={reset}
            className="px-4 py-2 rounded-lg bg-brown-700 text-white text-sm font-medium hover:bg-brown-800"
          >
            Try again
          </button>
          <Link
            href="/dashboard"
            className="px-4 py-2 rounded-lg border border-brown-200 text-brown-800 text-sm font-medium hover:bg-brown-50"
          >
            Back to Dashboard
          </Link>
        </div>
      </div>
    </main>
  );
}
