'use client';

import { motion, useReducedMotion } from 'framer-motion';
import { usePathname } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';

/** Total time the overlay stays mounted (slightly longer than Framer duration so the sweep finishes cleanly). */
const SWEEP_MS = 2800;
const SWEEP_DURATION_S = 2.45;

/**
 * Client navigations: instant scroll to top (less jumpy than leaving scroll position)
 * and a slow diagonal linear beige → brown sweep. Respects reduced motion.
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
            className="absolute left-1/2 top-1/2 h-[320vmax] w-[90vmin] max-w-none -translate-x-1/2 -translate-y-1/2 rounded-sm shadow-[0_0_80px_rgba(139,90,60,0.12)]"
            style={{
              background: 'linear-gradient(180deg, #faf5eb 0%, #4a3428 100%)',
              rotate: -42,
              willChange: 'transform, opacity',
            }}
            initial={{ x: '-140vmin', y: '-40vmin', opacity: 0.92 }}
            animate={{ x: '140vmin', y: '40vmin', opacity: 0 }}
            transition={{ duration: SWEEP_DURATION_S, ease: [0.33, 0, 0.2, 1] }}
          />
        </div>
      )}
    </>
  );
}
