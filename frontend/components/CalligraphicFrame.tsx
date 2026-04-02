'use client';

import { ReactNode } from 'react';

const VIEW_WIDTH = 400;
const VIEW_HEIGHT = 300;
/** Overall frame stroke width in viewBox units. */
const FRAME_STROKE = 14;
/** Inner trim line offset from content edge. */
const INNER_TRIM = 6;

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
          {/* Soft outer border: single rounded stroke so lines never overlap at corners */}
          <linearGradient id="frame-outer" x1="0" y1="0" x2="1" y2="1" gradientUnits="objectBoundingBox">
            <stop offset="0%" stopColor="#b8860b" stopOpacity={0.35} />
            <stop offset="50%" stopColor="#8b6914" stopOpacity={0.22} />
            <stop offset="100%" stopColor="#a67c52" stopOpacity={0.18} />
          </linearGradient>
          {/* Inner gold trim for a clean calm edge */}
          <linearGradient id="frame-trim" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="#e2b862" />
            <stop offset="100%" stopColor="#b8860b" />
          </linearGradient>
        </defs>

        {/* Outer frame: single rounded rectangle stroke, no overlapping segments */}
        <rect
          x={FRAME_STROKE / 2}
          y={FRAME_STROKE / 2}
          width={VIEW_WIDTH - FRAME_STROKE}
          height={VIEW_HEIGHT - FRAME_STROKE}
          fill="none"
          stroke="url(#frame-outer)"
          strokeWidth={FRAME_STROKE}
          rx={28}
        />

        {/* Inner trim: thin line just inside the border for definition */}
        <rect
          x={FRAME_STROKE + INNER_TRIM}
          y={FRAME_STROKE + INNER_TRIM}
          width={VIEW_WIDTH - 2 * (FRAME_STROKE + INNER_TRIM)}
          height={VIEW_HEIGHT - 2 * (FRAME_STROKE + INNER_TRIM)}
          fill="none"
          stroke="url(#frame-trim)"
          strokeWidth={1}
          rx={22}
          opacity={0.9}
        />
      </svg>
      <div className="relative z-10 h-full min-h-0">{children}</div>
    </div>
  );
}
