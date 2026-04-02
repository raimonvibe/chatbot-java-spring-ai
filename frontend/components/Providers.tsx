'use client';

import { DashboardNavProvider } from '@/context/DashboardNavContext';
import { ChatbotPreviewControlsProvider } from '@/context/ChatbotPreviewControlsContext';
import RouteTransitionProvider from '@/components/RouteTransitionProvider';

export default function Providers({ children }: { children: React.ReactNode }) {
  return (
    <DashboardNavProvider>
      <ChatbotPreviewControlsProvider>
        <RouteTransitionProvider>{children}</RouteTransitionProvider>
      </ChatbotPreviewControlsProvider>
    </DashboardNavProvider>
  );
}
