'use client';

import { motion, useReducedMotion } from 'framer-motion';
import { usePathname } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';

/** Total time the overlay stays mounted (slightly longer than Framer duration so the sweep finishes cleanly). */
const SWEEP_MS = 1400;
const SWEEP_DURATION_S = 0.74;
const TOOTH_COUNT = 7;

/**
 * Client navigations: instant scroll to top (less jumpy than leaving scroll position)
 * and a diagonal page-like sweep with a trailing toothed edge.
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
          <motion.div
            key={sweepKey}
            className="absolute left-1/2 top-1/2 h-[220vmax] w-[92vmin] max-w-none -translate-x-1/2 -translate-y-1/2"
            style={{ rotate: -42, willChange: 'transform, opacity' }}
            initial={{ x: '-142vmin', y: '-42vmin', opacity: 0 }}
            animate={{ x: '142vmin', y: '42vmin', opacity: [0, 0.92, 0] }}
            transition={{ duration: SWEEP_DURATION_S, times: [0, 0.2, 1], ease: [0.33, 0, 0.2, 1] }}
          >
            <div
              className="absolute inset-0"
              style={{
                background:
                  'linear-gradient(180deg, rgba(66,42,30,0) 0%, rgba(66,42,30,0.82) 24%, rgba(138,97,66,0.8) 50%, rgba(236,214,190,0.72) 72%, rgba(255,249,240,0) 100%)',
                clipPath: 'polygon(0% 8%, 100% 0%, 100% 92%, 0% 100%)',
                boxShadow:
                  'inset 0 0 0 1px rgba(255,244,228,0.28), inset 0 0 26px rgba(58,36,24,0.28)',
              }}
            />

            {Array.from({ length: TOOTH_COUNT }).map((_, idx) => {
              const y = (idx / TOOTH_COUNT) * 100;
              return (
                <div
                  key={`tooth-${idx}`}
                  className="absolute right-[-7vmin] h-[18%] w-[18vmin]"
                  style={{
                    top: `${y}%`,
                    background:
                      'linear-gradient(180deg, rgba(80,52,37,0.9) 0%, rgba(226,199,171,0.75) 70%, rgba(255,248,238,0.2) 100%)',
                    clipPath: 'polygon(0 0, 100% 50%, 0 100%, 18% 50%)',
                    filter: 'saturate(1.06)',
                  }}
                />
              );
            })}
          </motion.div>
        </div>
      )}
    </>
  );
}
