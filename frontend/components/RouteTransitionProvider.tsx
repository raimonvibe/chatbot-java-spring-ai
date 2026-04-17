'use client';

import { motion, useReducedMotion } from 'framer-motion';
import { usePathname } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';

/** Total time the overlay stays mounted (slightly longer than Framer duration so the sweep finishes cleanly). */
const SWEEP_MS = 1300;
const SWEEP_DURATION_S = 0.62;
const SWEEP_BLOCK_COUNT = 7;
const SWEEP_STAGGER_S = 0.055;

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
            const blockOpacity = 0.5 + ratio * 0.22;
            const blockOffset = (idx - (SWEEP_BLOCK_COUNT - 1) / 2) * 1.6;

            return (
              <motion.div
                key={`${sweepKey}-${idx}`}
                className="absolute left-1/2 top-1/2 h-[220vmax] max-w-none -translate-x-1/2 -translate-y-1/2"
                style={{
                  width: `${70 + idx * 3}vmin`,
                  background:
                    'linear-gradient(180deg, rgba(56,36,27,0) 0%, rgba(56,36,27,0.72) 22%, rgba(121,82,55,0.68) 46%, rgba(232,210,186,0.62) 64%, rgba(250,243,232,0.76) 78%, rgba(255,248,237,0) 100%)',
                  rotate: -42,
                  boxShadow:
                    'inset 0 0 0 1px rgba(255,245,230,0.24), inset 0 0 24px rgba(66,42,30,0.22)',
                  clipPath: `polygon(0% ${tiltInset}%, 100% 0%, 100% ${100 - tiltInset}%, 0% 100%)`,
                  willChange: 'transform, opacity',
                }}
                initial={{ x: `calc(-142vmin + ${blockOffset}vmin)`, y: '-42vmin', opacity: 0 }}
                animate={{ x: `calc(142vmin + ${blockOffset}vmin)`, y: '42vmin', opacity: [0, blockOpacity, 0] }}
                transition={{
                  duration: SWEEP_DURATION_S,
                  delay: idx * SWEEP_STAGGER_S,
                  times: [0, 0.24, 1],
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
