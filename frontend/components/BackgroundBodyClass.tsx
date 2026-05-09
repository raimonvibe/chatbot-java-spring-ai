'use client';

import { useEffect } from 'react';
import { usePathname } from 'next/navigation';

const DEFAULT_BODY_CLASS = 'app-photo-background';
const ACCOUNT_BODY_CLASS = 'app-account-background';

function shouldDisableBackground(pathname: string) {
  return pathname === '/privacy' || pathname === '/legal';
}

export default function BackgroundBodyClass() {
  const pathname = usePathname() || '/';

  useEffect(() => {
    const isAccountPage = pathname === '/account';
    const disable = shouldDisableBackground(pathname);
    document.body.classList.toggle(DEFAULT_BODY_CLASS, !disable && !isAccountPage);
    document.body.classList.toggle(ACCOUNT_BODY_CLASS, isAccountPage);

    return () => {
      document.body.classList.remove(DEFAULT_BODY_CLASS);
      document.body.classList.remove(ACCOUNT_BODY_CLASS);
    };
  }, [pathname]);

  return null;
}

