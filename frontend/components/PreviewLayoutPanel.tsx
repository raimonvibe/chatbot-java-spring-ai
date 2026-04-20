'use client';

import type { ChatbotPreviewControlsApi } from '@/context/ChatbotPreviewControlsContext';

type Props = {
  api: ChatbotPreviewControlsApi;
};

/**
 * Chatbot preview toolbar: viewport scale, scene, and device frame (shown inside Header dropdown).
 */
export default function PreviewLayoutPanel({ api }: Props) {
  const {
    theme,
    previewMode,
    setPreviewMode,
    sceneMode,
    setSceneMode,
    screenPreview,
    setScreenPreview,
    websitePreviewUrl,
  } = api;

  const pill = (selected: boolean) =>
    `inline-flex w-full min-h-[44px] min-w-0 items-center justify-center px-2 py-2 text-center text-xs leading-tight rounded-lg border transition-colors sm:min-h-0 sm:px-3 sm:py-1.5 ${
      selected ? 'text-white' : 'bg-white text-brown-800 border-brown-200 hover:border-brown-300'
    }`;

  const pillStyle = (selected: boolean) =>
    selected
      ? {
          backgroundColor: theme.primaryColor,
          borderColor: theme.primaryColor,
          color: '#ffffff',
        }
      : { borderColor: '#e8d9c9' };

  return (
    <div className="space-y-3">
      <div>
        <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-wide text-brown-500">Viewport scale</p>
        <div className="grid grid-cols-2 gap-2">
          {(['actual', 'fit'] as const).map((mode) => (
            <button
              key={mode}
              type="button"
              onClick={() => setPreviewMode(mode)}
              className={pill(previewMode === mode)}
              style={pillStyle(previewMode === mode)}
            >
              {mode === 'fit' ? 'Fit to screen' : 'Actual size'}
            </button>
          ))}
        </div>
      </div>
      <div className="border-t border-brown-100 pt-3">
        <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-wide text-brown-500">Background</p>
        <div className="grid grid-cols-2 gap-2">
          {(['plain', 'website'] as const).map((mode) => (
            <button
              key={mode}
              type="button"
              onClick={() => setSceneMode(mode)}
              disabled={mode === 'website' && !websitePreviewUrl}
              title={mode === 'website' && !websitePreviewUrl ? 'No safe website URL on this chatbot' : undefined}
              className={`${pill(sceneMode === mode)} disabled:cursor-not-allowed disabled:opacity-50`}
              style={pillStyle(sceneMode === mode)}
            >
              {mode === 'plain' ? 'Plain background' : 'Website background'}
            </button>
          ))}
        </div>
      </div>
      <div className="border-t border-brown-100 pt-3">
        <p className="mb-1.5 text-[11px] font-semibold uppercase tracking-wide text-brown-500">Device frame</p>
        <div className="grid grid-cols-3 gap-2">
          {(['desktop', 'tablet', 'mobile'] as const).map((size) => (
            <button
              key={size}
              type="button"
              onClick={() => setScreenPreview(size)}
              className={pill(screenPreview === size)}
              style={pillStyle(screenPreview === size)}
            >
              {size === 'desktop' ? 'Desktop' : size === 'tablet' ? 'Tablet' : 'Mobile'}
            </button>
          ))}
        </div>
      </div>
      <p className="text-[11px] leading-snug text-brown-600 border-t border-brown-100 pt-2">
        {previewMode === 'actual'
          ? 'On a phone, swipe sideways in the preview when the frame is wider than your screen.'
          : 'Fit mode scales the preview to your screen width.'}
      </p>
    </div>
  );
}
