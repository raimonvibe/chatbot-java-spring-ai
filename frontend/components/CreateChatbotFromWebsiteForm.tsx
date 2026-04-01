'use client';

import { useMemo, useState } from 'react';
import { Loader2, ArrowRight, CheckCircle, ChevronDown } from 'lucide-react';
import { previewWebsiteUrlInput, shouldShowIdnHostnameNote } from '@/lib/websiteUrlPreview';

export type CreateChatbotFromWebsiteFormVariant = 'onboarding' | 'dashboard';

type Props = {
  variant: CreateChatbotFromWebsiteFormVariant;
  onSubmit: (canonicalUrl: string) => Promise<void>;
  submitting: boolean;
  serverError?: string;
  onClearServerError?: () => void;
};

export default function CreateChatbotFromWebsiteForm({
  variant,
  onSubmit,
  submitting,
  serverError,
  onClearServerError,
}: Props) {
  const [url, setUrl] = useState('');
  const [localError, setLocalError] = useState('');

  const preview = useMemo(() => previewWebsiteUrlInput(url), [url]);
  const showPuny = preview.ok && shouldShowIdnHostnameNote(url, preview.hostname);
  const showExpandedPreview = preview.ok && preview.displayHref && (showPuny || (preview.notices?.length ?? 0) > 0);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLocalError('');
    onClearServerError?.();

    if (!preview.ok || !preview.displayHref) {
      setLocalError(preview.issues[0] ?? 'Invalid URL.');
      return;
    }

    await onSubmit(preview.displayHref);
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-4">
      <div>
        <label htmlFor="websiteUrl" className="block text-sm font-medium mb-2 text-brown-800">
          Website URL
        </label>
        <input
          id="websiteUrl"
          type="text"
          value={url}
          onChange={(e) => {
            setUrl(e.target.value);
            setLocalError('');
            onClearServerError?.();
          }}
          placeholder="https://example.com"
          className="w-full min-h-12 px-4 py-3 border border-brown-300 rounded-lg focus:ring-2 focus:ring-brown-500 focus:border-transparent bg-white text-brown-900 text-base"
          disabled={submitting}
          autoComplete="url"
          required
        />

        {preview.ok && preview.displayHref && !showExpandedPreview && (
          <p className="mt-2 text-xs text-brown-600 truncate" title={preview.displayHref}>
            Using: <span className="font-mono">{preview.displayHref}</span>
          </p>
        )}

        <details className="group mt-2 rounded-lg border border-brown-200/80 bg-brown-50/50 text-left [touch-action:manipulation]">
          <summary className="flex min-h-10 cursor-pointer items-center justify-between gap-2 px-3 py-2 text-xs font-medium text-brown-600 list-none select-none [&::-webkit-details-marker]:hidden">
            <span>How we use this</span>
            <ChevronDown
              className="h-4 w-4 shrink-0 text-brown-500 transition-transform duration-200 group-open:rotate-180"
              aria-hidden
            />
          </summary>
          <p className="border-t border-brown-200/80 px-3 py-2 text-xs text-brown-600 leading-relaxed">
            We read public pages from your site to train the chatbot. Use a URL you own or are allowed to crawl.
          </p>
        </details>
      </div>

      {showExpandedPreview && preview.displayHref && (
        <div className="rounded-lg border border-brown-200 bg-white/90 p-3 text-xs space-y-2 overflow-x-auto">
          <p className="font-mono text-brown-900 break-all">{preview.displayHref}</p>
          {showPuny && preview.hostname && (
            <p className="text-brown-700">
              Hostname (punycode): <span className="font-mono break-all">{preview.hostname}</span>
            </p>
          )}
          {preview.notices.map((n) => (
            <p key={n} className="text-amber-800 bg-amber-50 border border-amber-100 rounded px-2 py-1.5">
              {n}
            </p>
          ))}
        </div>
      )}

      {!preview.ok && preview.issues.length > 0 && url.trim().length > 0 && (
        <div className="rounded-lg border border-red-100 bg-red-50/80 px-3 py-2 text-sm text-red-800">{preview.issues[0]}</div>
      )}

      {localError && (
        <div className="rounded-lg border border-red-100 bg-red-50/90 px-3 py-2 text-sm text-red-800" role="alert">
          {localError}
        </div>
      )}
      {serverError && (
        <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-2.5 text-sm text-amber-950" role="alert">
          {serverError}
        </div>
      )}

      <button
        type="submit"
        disabled={submitting || !url.trim() || !preview.ok}
        className="w-full min-h-12 px-6 py-3 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-xl text-base font-semibold hover:shadow-lg transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 [touch-action:manipulation]"
      >
        {submitting ? (
          <>
            <Loader2 className="w-5 h-5 animate-spin" />
            Creating your chatbot...
          </>
        ) : variant === 'onboarding' ? (
          <>
            Create My Chatbot
            <ArrowRight className="w-5 h-5" />
          </>
        ) : (
          <>
            <CheckCircle className="w-5 h-5" /> Create My Chatbot
          </>
        )}
      </button>
    </form>
  );
}
