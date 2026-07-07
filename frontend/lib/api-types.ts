export interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  timestamp: number;
}

export interface ChatResponse {
  message: string;
  sessionId: string;
  timestamp: number;
  chatbotId: number;
}

export interface Chatbot {
  id: number;
  name: string;
  description: string;
  primaryLanguage: string;
  supportedLanguages: string[];
  brandingConfig: string;
  websiteUrl?: string;
  christianMessagingEnabled?: boolean;
  jesusTeachingsEnabled?: boolean;
  bibleVerse?: string;
  /** Avatar image id: "1".."12" or null/empty for no avatar. Server-validated. */
  avatarId?: string | null;
}

/** Allowed avatar ids for picker and display (must match backend EmbedSecurity.ALLOWED_AVATAR_IDS). */
export const AVATAR_IDS = [
  '1',
  '2',
  '3',
  '4',
  '5',
  '6',
  '7',
  '8',
  '9',
  '10',
  '11',
  '12',
] as const;
export type AvatarId = (typeof AVATAR_IDS)[number];

export interface JesusTeachingPreview {
  reference: string;
  text: string;
  similarity: string;
}

export interface JesusTeachingsPreviewResponse {
  chatbotId: string;
  websiteUrl: string;
  topTeachings: JesusTeachingPreview[];
  totalJesusVerses: number;
}

export interface VerseMatch {
  id: number;
  reference: string;
  book: string;
  chapter: number;
  verse: number;
  text: string;
  translation: string;
  similarity: number;
  similarityPercentage: number;
}

export interface ChristianContentAnalysis {
  chatbotId: number;
  websiteUrl: string;
  themes?: string[];
  relevantVerses: VerseMatch[];
  averageSimilarity: number;
  totalVersesAnalyzed: number;
  versesAboveThreshold: number;
}

export interface AuthUser {
  id?: number;
  username?: string;
  email?: string;
  name?: string;
  authProvider?: string;
  picture?: string;
}

export interface AnalysisStatus {
  ready: boolean;
  pagesIndexed?: number;
}

export interface SubscriptionStatus {
  isPreviewMode: boolean;
  canAccessIntegrationScript: boolean;
  maxChatbots: number;
  currentChatbotCount: number;
  plan?: string;
  /** Mirrors backend when billing integration is off (Stripe checkout/portal hidden). */
  billingEnabled?: boolean;
  paymentActionsAvailable?: boolean;
  /** From GET /api/subscription/status — min(monthly headroom, daily headroom). */
  websiteScansRemaining?: number;
  websiteScansMonthlyQuota?: number;
  websiteScansUsedThisMonth?: number;
  websiteScansDailyLimit?: number;
  websiteScansUsedRollingDay?: number;
}

/** Response from GET /api/subscription/status */
export interface SubscriptionStatusApi {
  hasSubscription: boolean;
  status: string;
  plan: string;
  isActive: boolean;
  canUseChatbot: boolean;
  currentPeriodEnd?: string;
  canceledAt?: string;
  billingEnabled?: boolean;
  paymentActionsAvailable?: boolean;
  websiteScansMonthlyQuota?: number;
  websiteScansUsedThisMonth?: number;
  websiteScansDailyLimit?: number;
  websiteScansUsedRollingDay?: number;
  websiteScansRemaining?: number;
}

/** Public GET /api/plans/limits (no auth). */
export interface PublicPlanLimitsResponse {
  description?: string;
  billingEnabled?: boolean;
  maxPagesPerScanOffered?: number;
  websiteScanPolicySummary?: string;
  plans?: Record<string, { maxPagesPerScan: number; messagesPerDay: number; monthlyScanQuota: number; maxChatbots?: number }>;
  standardPageTiers?: Record<string, number>;
}

/** Result of sync-from-session: success with data, or failure with error message for UI */
export type SyncFromSessionResult =
  | { ok: true; data: SubscriptionStatusApi & { synced?: boolean } }
  | { ok: false; error: string };
