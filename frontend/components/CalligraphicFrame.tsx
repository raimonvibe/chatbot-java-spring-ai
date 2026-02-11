'use client';

import { ReactNode } from 'react';

const VIEW_WIDTH = 400;
const VIEW_HEIGHT = 300;
/** Border width in viewBox units; visual thickness is relative. */
const BORDER = 16;
/** Inner trim line offset from content edge. */
const INNER_TRIM = 2;

/**
 * Ornamental frame around the chat window: soft gradient border and thin gold trim.
 * No corner flourishes—clean lining only.
 */
export default function CalligraphicFrame({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`relative ${className}`}>
      <svg
        className="absolute inset-0 w-full h-full pointer-events-none"
        viewBox={`0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`}
        preserveAspectRatio="none"
        aria-hidden
      >
        <defs>
          {/* Soft outer border: warm gradient, no dense pattern */}
          <linearGradient id="frame-outer" x1="0" y1="0" x2="1" y2="1" gradientUnits="objectBoundingBox">
            <stop offset="0%" stopColor="#b8860b" stopOpacity={0.35} />
            <stop offset="50%" stopColor="#8b6914" stopOpacity={0.25} />
            <stop offset="100%" stopColor="#a67c52" stopOpacity={0.2} />
          </linearGradient>
          {/* Inner gold trim for a clean edge */}
          <linearGradient id="frame-trim" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#d4a84b" />
            <stop offset="100%" stopColor="#b8860b" />
          </linearGradient>
        </defs>

        {/* Outer frame: four sides only (border), no fill over content */}
        <rect x={0} y={0} width={VIEW_WIDTH} height={BORDER} fill="url(#frame-outer)" />
        <rect x={VIEW_WIDTH - BORDER} y={0} width={BORDER} height={VIEW_HEIGHT} fill="url(#frame-outer)" />
        <rect x={0} y={VIEW_HEIGHT - BORDER} width={VIEW_WIDTH} height={BORDER} fill="url(#frame-outer)" />
        <rect x={0} y={0} width={BORDER} height={VIEW_HEIGHT} fill="url(#frame-outer)" />

        {/* Inner trim: thin line just inside the border for definition */}
        <rect
          x={BORDER + INNER_TRIM}
          y={BORDER + INNER_TRIM}
          width={VIEW_WIDTH - 2 * (BORDER + INNER_TRIM)}
          height={VIEW_HEIGHT - 2 * (BORDER + INNER_TRIM)}
          fill="none"
          stroke="url(#frame-trim)"
          strokeWidth={0.8}
          rx={BORDER * 0.2}
          opacity={0.9}
        />
      </svg>
      <div className="relative z-10 h-full min-h-0">{children}</div>
    </div>
  );
}
