'use client';

import { motion, useReducedMotion } from 'framer-motion';
import { usePathname } from 'next/navigation';
import { useEffect, useRef, useState } from 'react';

/** Total time the overlay stays mounted (slightly longer than Framer duration so the sweep finishes cleanly). */
const SWEEP_MS = 1900;
const SWEEP_DURATION_S = 1.05;
const TOOTH_COUNT = 7;
const TOOTH_DEPTH_PCT = 10;

function buildToothedClipPath(teeth: number, depthPct: number) {
  const points: string[] = ['0% 8%', '100% 0%'];
  const segment = 100 / teeth;

  for (let i = 0; i < teeth; i += 1) {
    const yMid = i * segment + segment / 2;
    const yEnd = (i + 1) * segment;
    points.push(`${100 - depthPct}% ${yMid}%`, `100% ${yEnd}%`);
  }

  points.push('0% 100%');
  return `polygon(${points.join(', ')})`;
}

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
  const toothedClipPath = buildToothedClipPath(TOOTH_COUNT, TOOTH_DEPTH_PCT);

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
            animate={{ x: '142vmin', y: '42vmin', opacity: [0, 0.96, 0.96, 0] }}
            transition={{ duration: SWEEP_DURATION_S, times: [0, 0.18, 0.82, 1], ease: [0.33, 0, 0.2, 1] }}
          >
            <div
              className="absolute inset-0"
              style={{
                background:
                  'linear-gradient(180deg, rgba(59,37,26,0) 0%, rgba(59,37,26,0.9) 26%, rgba(125,84,54,0.84) 52%, rgba(240,219,196,0.72) 74%, rgba(255,249,241,0) 100%)',
                clipPath: toothedClipPath,
                boxShadow:
                  'inset 0 0 0 1px rgba(255,244,228,0.28), inset 0 0 26px rgba(58,36,24,0.28)',
              }}
            />
          </motion.div>
        </div>
      )}
    </>
  );
}
