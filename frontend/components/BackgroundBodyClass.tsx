'use client';

import { useEffect } from 'react';
import { usePathname } from 'next/navigation';

const BODY_CLASS = 'app-photo-background';

function shouldDisableBackground(pathname: string) {
  return pathname === '/privacy' || pathname === '/legal';
}

export default function BackgroundBodyClass() {
  const pathname = usePathname() || '/';

  useEffect(() => {
    const disable = shouldDisableBackground(pathname);
    document.body.classList.toggle(BODY_CLASS, !disable);

    return () => {
      document.body.classList.remove(BODY_CLASS);
    };
  }, [pathname]);

  return null;
}

