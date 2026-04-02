'use client';

import { createContext, useCallback, useContext, useMemo, useState, type ReactNode } from 'react';

export type ChatbotPreviewControlsApi = {
  theme: { primaryColor: string };
  previewMode: 'fit' | 'actual';
  setPreviewMode: (m: 'fit' | 'actual') => void;
  sceneMode: 'plain' | 'website';
  setSceneMode: (m: 'plain' | 'website') => void;
  screenPreview: 'desktop' | 'tablet' | 'mobile';
  setScreenPreview: (s: 'desktop' | 'tablet' | 'mobile') => void;
  websitePreviewUrl: string | null;
};

type CtxValue = {
  controls: ChatbotPreviewControlsApi | null;
  setControls: (api: ChatbotPreviewControlsApi | null) => void;
};

const ChatbotPreviewControlsContext = createContext<CtxValue | null>(null);

export function ChatbotPreviewControlsProvider({ children }: { children: ReactNode }) {
  const [controls, setControlsState] = useState<ChatbotPreviewControlsApi | null>(null);
  const setControls = useCallback((api: ChatbotPreviewControlsApi | null) => {
    setControlsState(api);
  }, []);
  const value = useMemo(() => ({ controls, setControls }), [controls, setControls]);
  return (
    <ChatbotPreviewControlsContext.Provider value={value}>{children}</ChatbotPreviewControlsContext.Provider>
  );
}

export function useChatbotPreviewControlsRegistration() {
  const ctx = useContext(ChatbotPreviewControlsContext);
  if (!ctx) {
    throw new Error('useChatbotPreviewControlsRegistration must be used within ChatbotPreviewControlsProvider');
  }
  return ctx;
}

/** Header / shell: returns null when no chatbot preview has registered controls. */
export function useChatbotPreviewControls(): ChatbotPreviewControlsApi | null {
  const ctx = useContext(ChatbotPreviewControlsContext);
  if (!ctx) return null;
  return ctx.controls;
}
