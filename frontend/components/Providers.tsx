'use client';

import { DashboardNavProvider } from '@/context/DashboardNavContext';

export default function Providers({ children }: { children: React.ReactNode }) {
  return <DashboardNavProvider>{children}</DashboardNavProvider>;
}
