'use client';

import { ReactNode } from 'react';
import { PatternLines, PatternWaves } from '@visx/pattern';

const FRAME_THICKNESS = 14;
const VIEW_WIDTH = 400;
const VIEW_HEIGHT = 300;

/** Ornamental frame around the chat window using @visx/pattern (lines + waves) and calligraphic corners. Theme: brown/gold. */
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
          <PatternLines
            id="frame-lines"
            height={5}
            width={5}
            stroke="#a67c52"
            strokeWidth={0.8}
            orientation={['diagonal', 'diagonalRightToLeft']}
          />
          <PatternWaves
            id="frame-waves"
            height={4}
            width={4}
            stroke="#8b5a3c"
            strokeWidth={0.6}
          />
          <linearGradient id="frame-stroke" x1="0" y1="0" x2={VIEW_WIDTH} y2={VIEW_HEIGHT}>
            <stop offset="0%" stopColor="#a67c52" />
            <stop offset="50%" stopColor="#8b5a3c" />
            <stop offset="100%" stopColor="#ca8a04" />
          </linearGradient>
          <linearGradient id="frame-fill" x1="0" y1="0" x2="0" y2="1">
            <stop offset="0%" stopColor="rgba(166,124,82,0.08)" />
            <stop offset="100%" stopColor="rgba(202,138,4,0.05)" />
          </linearGradient>
        </defs>
        {/* Full frame: four sides with ornamental pattern (around the whole window) */}
        <rect x={0} y={0} width={VIEW_WIDTH} height={FRAME_THICKNESS} fill="url(#frame-lines)" opacity={0.85} />
        <rect x={VIEW_WIDTH - FRAME_THICKNESS} y={0} width={FRAME_THICKNESS} height={VIEW_HEIGHT} fill="url(#frame-lines)" opacity={0.85} />
        <rect x={0} y={VIEW_HEIGHT - FRAME_THICKNESS} width={VIEW_WIDTH} height={FRAME_THICKNESS} fill="url(#frame-lines)" opacity={0.85} />
        <rect x={0} y={0} width={FRAME_THICKNESS} height={VIEW_HEIGHT} fill="url(#frame-lines)" opacity={0.85} />
        {/* Inner wave accent along edges (subtle) */}
        <rect x={FRAME_THICKNESS} y={FRAME_THICKNESS} width={VIEW_WIDTH - 2 * FRAME_THICKNESS} height={2} fill="url(#frame-waves)" opacity={0.4} />
        <rect x={VIEW_WIDTH - FRAME_THICKNESS - 2} y={FRAME_THICKNESS} width={2} height={VIEW_HEIGHT - 2 * FRAME_THICKNESS} fill="url(#frame-waves)" opacity={0.4} />
        <rect x={FRAME_THICKNESS} y={VIEW_HEIGHT - FRAME_THICKNESS - 2} width={VIEW_WIDTH - 2 * FRAME_THICKNESS} height={2} fill="url(#frame-waves)" opacity={0.4} />
        <rect x={FRAME_THICKNESS} y={FRAME_THICKNESS} width={2} height={VIEW_HEIGHT - 2 * FRAME_THICKNESS} fill="url(#frame-waves)" opacity={0.4} />
        {/* Calligraphic corner flourishes (on top of pattern) */}
        <path
          d="M 0 24 Q 0 0 24 0 L 36 0 Q 44 0 46 8 L 50 18 Q 52 24 46 28 L 32 34 Q 26 38 20 32 L 10 22 Q 4 16 0 24 Z"
          fill="url(#frame-fill)"
          stroke="url(#frame-stroke)"
          strokeWidth="1"
          strokeLinejoin="round"
          opacity={0.95}
        />
        <path d="M 0 42 L 0 30 Q 6 34 14 28 L 24 20" fill="none" stroke="url(#frame-stroke)" strokeWidth="0.7" opacity={0.6} />
        <path
          d={`M ${VIEW_WIDTH} 24 Q ${VIEW_WIDTH} 0 ${VIEW_WIDTH - 24} 0 L ${VIEW_WIDTH - 36} 0 Q ${VIEW_WIDTH - 44} 0 ${VIEW_WIDTH - 46} 8 L ${VIEW_WIDTH - 50} 18 Q ${VIEW_WIDTH - 52} 24 ${VIEW_WIDTH - 46} 28 L ${VIEW_WIDTH - 32} 34 Q ${VIEW_WIDTH - 26} 38 ${VIEW_WIDTH - 20} 32 L ${VIEW_WIDTH - 10} 22 Q ${VIEW_WIDTH - 4} 16 ${VIEW_WIDTH} 24 Z`}
          fill="url(#frame-fill)"
          stroke="url(#frame-stroke)"
          strokeWidth="1"
          strokeLinejoin="round"
          opacity={0.95}
        />
        <path d={`M ${VIEW_WIDTH} 42 L ${VIEW_WIDTH} 30 Q ${VIEW_WIDTH - 6} 34 ${VIEW_WIDTH - 14} 28 L ${VIEW_WIDTH - 24} 20`} fill="none" stroke="url(#frame-stroke)" strokeWidth="0.7" opacity={0.6} />
        <path
          d={`M 0 ${VIEW_HEIGHT - 24} Q 0 ${VIEW_HEIGHT} 24 ${VIEW_HEIGHT} L 36 ${VIEW_HEIGHT} Q 44 ${VIEW_HEIGHT} 46 ${VIEW_HEIGHT - 8} L 50 ${VIEW_HEIGHT - 18} Q 52 ${VIEW_HEIGHT - 24} 46 ${VIEW_HEIGHT - 28} L 32 ${VIEW_HEIGHT - 34} Q 26 ${VIEW_HEIGHT - 38} 20 ${VIEW_HEIGHT - 32} L 10 ${VIEW_HEIGHT - 22} Q 4 ${VIEW_HEIGHT - 16} 0 ${VIEW_HEIGHT - 24} Z`}
          fill="url(#frame-fill)"
          stroke="url(#frame-stroke)"
          strokeWidth="1"
          strokeLinejoin="round"
          opacity={0.95}
        />
        <path d={`M 0 ${VIEW_HEIGHT - 42} L 0 ${VIEW_HEIGHT - 30} Q 6 ${VIEW_HEIGHT - 34} 14 ${VIEW_HEIGHT - 28} L 24 ${VIEW_HEIGHT - 20}`} fill="none" stroke="url(#frame-stroke)" strokeWidth="0.7" opacity={0.6} />
        <path
          d={`M ${VIEW_WIDTH} ${VIEW_HEIGHT - 24} Q ${VIEW_WIDTH} ${VIEW_HEIGHT} ${VIEW_WIDTH - 24} ${VIEW_HEIGHT} L ${VIEW_WIDTH - 36} ${VIEW_HEIGHT} Q ${VIEW_WIDTH - 44} ${VIEW_HEIGHT} ${VIEW_WIDTH - 46} ${VIEW_HEIGHT - 8} L ${VIEW_WIDTH - 50} ${VIEW_HEIGHT - 18} Q ${VIEW_WIDTH - 52} ${VIEW_HEIGHT - 24} ${VIEW_WIDTH - 46} ${VIEW_HEIGHT - 28} L ${VIEW_WIDTH - 32} ${VIEW_HEIGHT - 34} Q ${VIEW_WIDTH - 26} ${VIEW_HEIGHT - 38} ${VIEW_WIDTH - 20} ${VIEW_HEIGHT - 32} L ${VIEW_WIDTH - 10} ${VIEW_HEIGHT - 22} Q ${VIEW_WIDTH - 4} ${VIEW_HEIGHT - 16} ${VIEW_WIDTH} ${VIEW_HEIGHT - 24} Z`}
          fill="url(#frame-fill)"
          stroke="url(#frame-stroke)"
          strokeWidth="1"
          strokeLinejoin="round"
          opacity={0.95}
        />
        <path d={`M ${VIEW_WIDTH} ${VIEW_HEIGHT - 42} L ${VIEW_WIDTH} ${VIEW_HEIGHT - 30} Q ${VIEW_WIDTH - 6} ${VIEW_HEIGHT - 34} ${VIEW_WIDTH - 14} ${VIEW_HEIGHT - 28} L ${VIEW_WIDTH - 24} ${VIEW_HEIGHT - 20}`} fill="none" stroke="url(#frame-stroke)" strokeWidth="0.7" opacity={0.6} />
      </svg>
      <div className="relative z-10 h-full min-h-0">{children}</div>
    </div>
  );
}
