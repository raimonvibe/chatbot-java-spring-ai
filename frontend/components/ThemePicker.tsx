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
      <div className="grid grid-cols-4 sm:grid-cols-4 gap-2">
        {PASTEL_PRESETS.map((theme) => {
          const isSelected =
            current.primary === theme.primaryColor && current.secondary === theme.secondaryColor;
          return (
            <button
              key={theme.id}
              type="button"
              disabled={applying}
              onClick={(e) => {
                e.preventDefault();
                e.stopPropagation();
                onApply(theme);
              }}
              onPointerDown={(e) => e.currentTarget.setPointerCapture?.(e.pointerId)}
              aria-label={`Theme ${theme.name}`}
              title={theme.name}
              className={`
                flex flex-col items-center justify-center gap-1 p-2 rounded-lg border-2 transition-all min-w-0
                min-h-[44px] sm:min-h-0
                focus:outline-none focus:ring-2 focus:ring-brown-500 focus:ring-offset-1
                disabled:opacity-50 disabled:cursor-not-allowed cursor-pointer touch-manipulation
                ${isSelected
                  ? 'border-brown-600 bg-brown-50'
                  : 'border-brown-200 hover:border-brown-400 hover:bg-brown-50/50 active:bg-brown-100'
                }
              `}
              style={{ touchAction: 'manipulation' }}
            >
              <span
                className="w-8 h-8 rounded-full flex-shrink-0 border border-brown-200 shadow-inner pointer-events-none"
                style={{ backgroundColor: theme.primaryColor }}
              />
              <span className="text-[10px] sm:text-xs font-medium text-brown-700 truncate w-full text-center pointer-events-none">
                {theme.name}
              </span>
            </button>
          );
        })}
      </div>
    </div>
  );
}

export { PASTEL_PRESETS };
