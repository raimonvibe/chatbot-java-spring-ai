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
  const [confirmed, setConfirmed] = useState(false);
  const [localError, setLocalError] = useState('');

  const preview = useMemo(() => previewWebsiteUrlInput(url), [url]);
  const showPuny = preview.ok && shouldShowIdnHostnameNote(url, preview.hostname);

  const combinedError = serverError || localError;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setLocalError('');
    onClearServerError?.();

    if (!preview.ok || !preview.displayHref) {
      setLocalError(preview.issues[0] ?? 'Invalid URL.');
      return;
    }
    if (!confirmed) {
      setLocalError('Please confirm the address above before continuing.');
      return;
    }

    await onSubmit(preview.displayHref);
  };

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      <div>
        <label htmlFor="websiteUrl" className="block text-sm sm:text-base font-medium mb-2 text-brown-800">
          Enter your website URL
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
          placeholder="e.g. https://example.com or your-domain.com"
          className="w-full min-h-12 px-3.5 sm:px-4 py-3 border border-brown-300 rounded-lg focus:ring-2 focus:ring-brown-500 focus:border-transparent bg-white text-brown-900 text-base sm:text-lg"
          disabled={submitting}
          autoComplete="url"
          required
        />

        <details className="group mt-3 rounded-xl border border-brown-200/90 bg-brown-50/70 text-left shadow-sm open:shadow-md transition-shadow [touch-action:manipulation]">
          <summary className="flex min-h-12 sm:min-h-0 cursor-pointer items-center justify-between gap-3 px-3 sm:px-3.5 py-3 sm:py-2.5 text-sm font-medium text-brown-800 list-none select-none [&::-webkit-details-marker]:hidden">
            <span className="min-w-0 flex-1 text-pretty leading-snug sm:leading-normal">How we use your URL</span>
            <ChevronDown
              className="h-5 w-5 shrink-0 text-brown-500 transition-transform duration-200 group-open:rotate-180 sm:h-4 sm:w-4"
              aria-hidden
            />
          </summary>
          <div className="space-y-2 border-t border-brown-200/80 px-3 sm:px-3.5 py-3 text-sm leading-relaxed text-brown-600">
            <p>
              We analyze your site&apos;s public pages to train the chatbot on your content. Christian tone settings are on
              by default; you can adjust them later.
            </p>
            <p className="text-xs sm:text-sm text-brown-600 text-pretty">
              Only use URLs you own or are allowed to crawl. International domains and look-alike letters can mimic other
              brands - always match the address to the site you trust before confirming below.
            </p>
          </div>
        </details>
      </div>

      {preview.ok && preview.displayHref && (
        <div className="rounded-lg border border-brown-200 bg-white/90 p-3 sm:p-4 text-sm space-y-2 overflow-x-auto">
          <p className="font-medium text-brown-800 text-pretty">We will use this address (preview)</p>
          <p className="font-mono text-brown-900 break-all text-xs sm:text-sm">{preview.displayHref}</p>
          {showPuny && preview.hostname && (
            <p className="text-xs text-brown-700">
              Punycode hostname (what browsers resolve): <span className="font-mono break-all">{preview.hostname}</span>
            </p>
          )}
          <p className="text-xs text-brown-600 pt-1">
            Quick check: does this hostname match the real site you intend? If not, edit the URL above.
          </p>
          {preview.notices.map((n) => (
            <p key={n} className="text-xs text-amber-800 bg-amber-50 border border-amber-100 rounded px-2 py-1.5">
              {n}
            </p>
          ))}
        </div>
      )}

      {!preview.ok && preview.issues.length > 0 && url.trim().length > 0 && (
        <div className="rounded-lg border border-red-100 bg-red-50/80 px-3 py-2 text-sm text-red-800">{preview.issues[0]}</div>
      )}

      <label className="flex items-start gap-3 cursor-pointer select-none text-sm sm:text-[0.9375rem] text-brown-800 [touch-action:manipulation]">
        <input
          type="checkbox"
          className="mt-0.5 sm:mt-1 h-5 w-5 sm:h-4 sm:w-4 rounded border-brown-300 text-gold-600 focus:ring-brown-500 shrink-0"
          checked={confirmed}
          onChange={(e) => {
            setConfirmed(e.target.checked);
            setLocalError('');
            onClearServerError?.();
          }}
          disabled={submitting || !preview.ok}
        />
        <span className="min-w-0 text-pretty leading-snug">
          I confirm this is the correct website for my chatbot (matches what I intend and am allowed to use).
        </span>
      </label>

      {combinedError && (
        <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-red-700 text-sm" role="alert">
          {combinedError}
        </div>
      )}

      <button
        type="submit"
        disabled={submitting || !url.trim() || !preview.ok || !confirmed}
        className="w-full min-h-12 px-6 py-3.5 sm:py-3 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-xl text-base sm:text-[0.95rem] font-semibold hover:shadow-lg transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2 [touch-action:manipulation]"
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
