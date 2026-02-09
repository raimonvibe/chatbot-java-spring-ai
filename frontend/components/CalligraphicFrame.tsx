'use client';

import { ReactNode } from 'react';

/** Calligraphic/ornamental SVG decorations around the chat window — theme colors brown/gold. Static SVG only (no user content). */
export default function CalligraphicFrame({ children, className = '' }: { children: ReactNode; className?: string }) {
  return (
    <div className={`relative ${className}`}>
      {/* Decorative corner flourishes; SVG scales with container */}
      <svg
        className="absolute inset-0 w-full h-full pointer-events-none"
        viewBox="0 0 400 300"
        preserveAspectRatio="none"
        aria-hidden
      >
        <defs>
          <linearGradient id="frame-stroke" x1="0" y1="0" x2="400" y2="300">
            <stop offset="0%" stopColor="#a67c52" />
            <stop offset="50%" stopColor="#8b5a3c" />
            <stop offset="100%" stopColor="#ca8a04" />
          </linearGradient>
          <linearGradient id="frame-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="rgba(166,124,82,0.06)" />
            <stop offset="100%" stopColor="rgba(202,138,4,0.04)" />
          </linearGradient>
        </defs>
        {/* Top-left corner flourish (calligraphic curl) */}
        <path
          d="M 0 24 Q 0 0 24 0 L 36 0 Q 44 0 46 8 L 50 18 Q 52 24 46 28 L 32 34 Q 26 38 20 32 L 10 22 Q 4 16 0 24 Z"
          fill="url(#frame-fill)"
          stroke="url(#frame-stroke)"
          strokeWidth="1"
          strokeLinejoin="round"
          opacity={0.9}
        />
        <path d="M 0 42 L 0 30 Q 6 34 14 28 L 24 20" fill="none" stroke="url(#frame-stroke)" strokeWidth="0.7" opacity={0.5} />
        {/* Top-right */}
        <path
          d="M 400 24 Q 400 0 376 0 L 364 0 Q 356 0 354 8 L 350 18 Q 348 24 354 28 L 368 34 Q 374 38 380 32 L 390 22 Q 396 16 400 24 Z"
          fill="url(#frame-fill)"
          stroke="url(#frame-stroke)"
          strokeWidth="1"
          strokeLinejoin="round"
          opacity={0.9}
        />
        <path d="M 400 42 L 400 30 Q 394 34 386 28 L 376 20" fill="none" stroke="url(#frame-stroke)" strokeWidth="0.7" opacity={0.5} />
        {/* Bottom-left */}
        <path
          d="M 0 276 Q 0 300 24 300 L 36 300 Q 44 300 46 292 L 50 282 Q 52 276 46 272 L 32 266 Q 26 262 20 268 L 10 278 Q 4 284 0 276 Z"
          fill="url(#frame-fill)"
          stroke="url(#frame-stroke)"
          strokeWidth="1"
          strokeLinejoin="round"
          opacity={0.9}
        />
        <path d="M 0 258 L 0 270 Q 6 266 14 272 L 24 280" fill="none" stroke="url(#frame-stroke)" strokeWidth="0.7" opacity={0.5} />
        {/* Bottom-right */}
        <path
          d="M 400 276 Q 400 300 376 300 L 364 300 Q 356 300 354 292 L 350 282 Q 348 276 354 272 L 368 266 Q 374 262 380 268 L 390 278 Q 396 284 400 276 Z"
          fill="url(#frame-fill)"
          stroke="url(#frame-stroke)"
          strokeWidth="1"
          strokeLinejoin="round"
          opacity={0.9}
        />
        <path d="M 400 258 L 400 270 Q 394 266 386 272 L 376 280" fill="none" stroke="url(#frame-stroke)" strokeWidth="0.7" opacity={0.5} />
      </svg>
      <div className="relative z-10 h-full min-h-0">{children}</div>
    </div>
  );
}
