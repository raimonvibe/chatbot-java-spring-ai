'use client';

import { motion, useReducedMotion } from 'framer-motion';
import { usePathname } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';

/** Total time the overlay stays mounted (slightly longer than Framer duration so the sweep finishes cleanly). */
const SWEEP_MS = 2800;
const SWEEP_DURATION_S = 2.45;
const SWEEP_BLOCK_COUNT = 7;
const SWEEP_STAGGER_S = 0.11;

/**
 * Client navigations: instant scroll to top (less jumpy than leaving scroll position)
 * and a slow diagonal glass-like sweep made of staggered cutout blocks.
 * Respects reduced motion.
 */
export default function RouteTransitionProvider({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const reduceMotion = useReducedMotion();
  const isFirstPath = useRef(true);
  const prevPath = useRef(pathname);
  const sweepId = useRef(0);
  const [sweepKey, setSweepKey] = useState(0);

  useEffect(() => {
    if (isFirstPath.current) {
      isFirstPath.current = false;
      prevPath.current = pathname;
      return;
    }
    if (prevPath.current === pathname) return;
    prevPath.current = pathname;

    window.scrollTo({ top: 0, left: 0, behavior: 'auto' });

    if (reduceMotion) return;

    const id = ++sweepId.current;
    setSweepKey(id);
    const t = window.setTimeout(() => {
      if (sweepId.current === id) setSweepKey(0);
    }, SWEEP_MS);
    return () => window.clearTimeout(t);
  }, [pathname, reduceMotion]);

  return (
    <>
      {children}
      {sweepKey > 0 && !reduceMotion && (
        <div
          aria-hidden
          className="pointer-events-none fixed inset-0 z-[200] overflow-hidden"
        >
          {Array.from({ length: SWEEP_BLOCK_COUNT }).map((_, idx) => {
            const ratio = idx / (SWEEP_BLOCK_COUNT - 1);
            const tiltInset = idx % 2 === 0 ? 10 : 18;
            const blockOpacity = 0.28 + ratio * 0.3;
            const blockBlur = 8 + idx * 0.75;

            return (
              <motion.div
                key={`${sweepKey}-${idx}`}
                className="absolute left-1/2 top-1/2 h-[320vmax] max-w-none -translate-x-1/2 -translate-y-1/2 shadow-[0_0_90px_rgba(122,90,70,0.18)]"
                style={{
                  width: `${76 + idx * 4}vmin`,
                  background:
                    'linear-gradient(180deg, rgba(74,52,40,0) 0%, rgba(74,52,40,0.26) 26%, rgba(186,154,126,0.46) 56%, rgba(250,245,235,0.34) 78%, rgba(250,245,235,0) 100%)',
                  rotate: -42,
                  mixBlendMode: 'screen',
                  filter: `blur(${blockBlur}px)`,
                  opacity: blockOpacity,
                  clipPath: `polygon(0% ${tiltInset}%, 100% 0%, 100% ${100 - tiltInset}%, 0% 100%)`,
                  willChange: 'transform, opacity',
                }}
                initial={{ x: '-142vmin', y: '-42vmin', opacity: 0 }}
                animate={{ x: '142vmin', y: '42vmin', opacity: 0 }}
                transition={{
                  duration: SWEEP_DURATION_S,
                  delay: idx * SWEEP_STAGGER_S,
                  ease: [0.33, 0, 0.2, 1],
                }}
              />
            );
          })}
        </div>
      )}
    </>
  );
}
