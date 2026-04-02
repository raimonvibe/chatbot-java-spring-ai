'use client';

import { createContext, useContext, useState, useCallback, type ReactNode } from 'react';

export interface DashboardNavValue {
  openSubscription: () => Promise<void>;
  logout: () => void;
  toggleCreateForm: () => void;
  showCreateForm: boolean;
  hasChatbots: boolean;
  /** When false, hide header/actions to add another chatbot (subscription limit). */
  canAddChatbot: boolean;
  isPreviewMode: boolean;
  onDeleteAllChatbots: () => void;
  /** When true, allow delete/testing reset controls to be shown in the UI. */
  canDeleteChatbots: boolean;
  portalLoading: boolean;
  /** When false, hide Stripe portal / “Subscription” entry (billing disabled on server). */
  showSubscriptionNav: boolean;
}

const DashboardNavContext = createContext<{
  nav: DashboardNavValue | null;
  setNav: (value: DashboardNavValue | null) => void;
}>({ nav: null, setNav: () => {} });

export function DashboardNavProvider({ children }: { children: ReactNode }) {
  const [nav, setNav] = useState<DashboardNavValue | null>(null);
  return (
    <DashboardNavContext.Provider value={{ nav, setNav }}>
      {children}
    </DashboardNavContext.Provider>
  );
}

export function useDashboardNav() {
  return useContext(DashboardNavContext).nav;
}

export function useSetDashboardNav() {
  return useContext(DashboardNavContext).setNav;
}
