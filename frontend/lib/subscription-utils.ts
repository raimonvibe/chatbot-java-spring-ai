import type { SubscriptionStatus, SubscriptionStatusApi } from '@/lib/api';
import { websiteScanFieldsFromSubscriptionApi } from '@/lib/api';

/** Maps authenticated subscription API response to dashboard UI state. */
export function mapSubscriptionFromApi(
  api: SubscriptionStatusApi | null | undefined,
  chatbotCount: number
): SubscriptionStatus {
  const canUse = !!api?.canUseChatbot;
  return {
    isPreviewMode: !canUse,
    canAccessIntegrationScript: canUse,
    maxChatbots: canUse ? 10 : 1,
    currentChatbotCount: chatbotCount,
    plan: api?.plan,
    billingEnabled: api?.billingEnabled,
    paymentActionsAvailable: api?.paymentActionsAvailable,
    ...websiteScanFieldsFromSubscriptionApi(api ?? null),
  };
}

export function previewSubscriptionFallback(chatbotCount: number): SubscriptionStatus {
  return {
    isPreviewMode: true,
    canAccessIntegrationScript: false,
    maxChatbots: 1,
    currentChatbotCount: chatbotCount,
    plan: undefined,
    billingEnabled: undefined,
    paymentActionsAvailable: undefined,
  };
}
