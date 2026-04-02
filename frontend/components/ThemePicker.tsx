'use client';

import { Palette } from 'lucide-react';

/** Allowed by backend: hex #xxx or #xxxxxx only. We use presets only (no user input) for security. */
export interface PastelTheme {
  id: string;
  name: string;
  primaryColor: string;
  secondaryColor: string;
  borderRadius?: string;
}

const PASTEL_PRESETS: PastelTheme[] = [
  { id: 'default', name: 'Default', primaryColor: '#5B8FB9', secondaryColor: '#6c757d', borderRadius: '8px' },
  { id: 'sage', name: 'Sage', primaryColor: '#7D9B69', secondaryColor: '#B5C9A8', borderRadius: '8px' },
  { id: 'sky', name: 'Sky', primaryColor: '#6BA3C6', secondaryColor: '#A8D0E6', borderRadius: '8px' },
  { id: 'lavender', name: 'Lavender', primaryColor: '#9B8BB5', secondaryColor: '#C9B8E0', borderRadius: '8px' },
  { id: 'blush', name: 'Blush', primaryColor: '#C99A9E', secondaryColor: '#E8C8CA', borderRadius: '8px' },
  { id: 'mint', name: 'Mint', primaryColor: '#5FB38A', secondaryColor: '#A3D9C2', borderRadius: '8px' },
  { id: 'peach', name: 'Peach', primaryColor: '#D4A574', secondaryColor: '#F0D4A8', borderRadius: '8px' },
  { id: 'warm', name: 'Warm', primaryColor: '#B8956B', secondaryColor: '#E8DCC4', borderRadius: '8px' },
];

export interface ThemePickerProps {
  /** Current brandingConfig JSON string (may be empty or {}) */
  currentBrandingConfig: string;
  onApply: (theme: PastelTheme) => void;
  applying?: boolean;
  className?: string;
}

/** Parse current primary/secondary from brandingConfig; safe and minimal. */
function parseCurrentTheme(configJson: string): { primary?: string; secondary?: string } {
  if (!configJson || !configJson.trim()) return {};
  try {
    const o = JSON.parse(configJson) as Record<string, unknown>;
    const primary = typeof o.primaryColor === 'string' ? o.primaryColor.trim() : undefined;
    const secondary = typeof o.secondaryColor === 'string' ? o.secondaryColor.trim() : undefined;
    return { primary, secondary };
  } catch {
    return {};
  }
}

export default function ThemePicker({ currentBrandingConfig, onApply, applying = false, className = '' }: ThemePickerProps) {
  const current = parseCurrentTheme(currentBrandingConfig);

  return (
    <div className={`rounded-xl border border-brown-200 bg-white p-4 ${className}`}>
      <div className="flex items-center gap-2 mb-3">
        <Palette className="w-4 h-4 text-brown-600 flex-shrink-0" aria-hidden />
        <span className="text-sm font-semibold text-brown-800">Match your site</span>
      </div>
      <p className="text-xs text-brown-600 mb-3">
        Choose a pastel theme for your chat widget. It will apply to the embed on your website.
      </p>
      <div className="grid grid-cols-2 gap-2 sm:grid-cols-4">
        {PASTEL_PRESETS.map((theme) => {
          const isSelected =
            current.primary === theme.primaryColor && current.secondary === theme.secondaryColor;
          return (
            <button
              key={theme.id}
              type="button"
              disabled={applying}
              onClick={() => onApply(theme)}
              aria-label={`Theme ${theme.name}, primary ${theme.primaryColor}, secondary ${theme.secondaryColor}`}
              title={`${theme.name} — ${theme.primaryColor} / ${theme.secondaryColor}`}
              className={`
                flex min-h-[44px] min-w-0 flex-col items-center justify-center gap-1 rounded-lg border-2 p-2 transition-all
                touch-manipulation sm:min-h-0
                focus:outline-none focus:ring-2 focus:ring-brown-500 focus:ring-offset-1
                disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer
                ${isSelected
                  ? 'border-brown-600 bg-brown-50'
                  : 'border-brown-200 hover:border-brown-400 hover:bg-brown-50/50 active:bg-brown-100'
                }
              `}
              style={{ touchAction: 'manipulation' }}
            >
              <span
                className="h-8 w-8 shrink-0 rounded-full border border-brown-200 shadow-inner"
                style={{ backgroundColor: theme.primaryColor }}
              />
              <span className="w-full text-center text-xs font-medium leading-snug text-pretty text-brown-800">
                {theme.name}
              </span>
              <span className="w-full text-center font-mono text-[9px] leading-tight text-brown-500 sm:text-[10px]">
                {theme.primaryColor}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

export { PASTEL_PRESETS };
