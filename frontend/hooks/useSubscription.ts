'use client';

import { useCallback, useEffect, useState } from 'react';
import {
  getSubscriptionStatusFromApi,
  type SubscriptionStatus,
  type SubscriptionStatusApi,
} from '@/lib/api';
import { mapSubscriptionFromApi, previewSubscriptionFallback } from '@/lib/subscription-utils';

export interface UseSubscriptionResult {
  status: SubscriptionStatus | null;
  apiData: SubscriptionStatusApi | null;
  loading: boolean;
  refresh: (chatbotCountOverride?: number) => Promise<void>;
}

/**
 * Shared subscription state for dashboard, account, onboarding, and preview pages.
 */
export function useSubscription(chatbotCount = 0): UseSubscriptionResult {
  const [status, setStatus] = useState<SubscriptionStatus | null>(null);
  const [apiData, setApiData] = useState<SubscriptionStatusApi | null>(null);
  const [loading, setLoading] = useState(true);

  const refresh = useCallback(
    async (chatbotCountOverride?: number) => {
      const count =
        typeof chatbotCountOverride === 'number' && chatbotCountOverride >= 0
          ? chatbotCountOverride
          : chatbotCount;
      try {
        const api = await getSubscriptionStatusFromApi();
        setApiData(api);
        setStatus(mapSubscriptionFromApi(api, count));
      } catch {
        setApiData(null);
        setStatus(previewSubscriptionFallback(count));
      } finally {
        setLoading(false);
      }
    },
    [chatbotCount]
  );

  useEffect(() => {
    void refresh();
  }, [refresh]);

  return { status, apiData, loading, refresh };
}
