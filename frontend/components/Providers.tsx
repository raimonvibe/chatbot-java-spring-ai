'use client';

import { DashboardNavProvider } from '@/context/DashboardNavContext';
import RouteTransitionProvider from '@/components/RouteTransitionProvider';

export default function Providers({ children }: { children: React.ReactNode }) {
  return (
    <DashboardNavProvider>
      <RouteTransitionProvider>{children}</RouteTransitionProvider>
    </DashboardNavProvider>
  );
}
