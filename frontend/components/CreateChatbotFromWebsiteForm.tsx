'use client';

import { useMemo, useState } from 'react';
import { Loader2, ArrowRight, CheckCircle } from 'lucide-react';
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
        <label htmlFor="websiteUrl" className="block text-sm font-medium mb-2 text-brown-800">
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
          className="w-full px-4 py-3 border border-brown-300 rounded-lg focus:ring-2 focus:ring-brown-500 focus:border-transparent bg-white text-brown-900 text-lg"
          disabled={submitting}
          autoComplete="url"
          required
        />
        <p className="text-sm text-brown-600 mt-2">
          We&apos;ll analyze your site and build a chatbot from its public content. Christian values are pre-configured by
          default.
        </p>
      </div>

      {preview.ok && preview.displayHref && (
        <div className="rounded-lg border border-brown-200 bg-white/90 p-4 text-sm space-y-2">
          <p className="font-medium text-brown-800">We will use this address (preview)</p>
          <p className="font-mono text-brown-900 break-all text-xs sm:text-sm">{preview.displayHref}</p>
          {showPuny && preview.hostname && (
            <p className="text-xs text-brown-700">
              Punycode hostname (what browsers resolve): <span className="font-mono break-all">{preview.hostname}</span>
            </p>
          )}
          <p className="text-xs text-brown-600 pt-1">
            Check carefully: look-alike letters and international domains can mimic other brands. Only continue if this is
            your site or one you are allowed to use.
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

      <label className="flex items-start gap-3 cursor-pointer select-none text-sm text-brown-800">
        <input
          type="checkbox"
          className="mt-1 w-4 h-4 rounded border-brown-300 text-gold-600 focus:ring-brown-500 shrink-0"
          checked={confirmed}
          onChange={(e) => {
            setConfirmed(e.target.checked);
            setLocalError('');
            onClearServerError?.();
          }}
          disabled={submitting || !preview.ok}
        />
        <span>
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
        className="w-full px-6 py-3 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-xl font-semibold hover:shadow-lg transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
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
