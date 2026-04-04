'use client';

import { useState, useEffect, useCallback } from 'react';
import { motion } from 'framer-motion';
import { getAllChatbots, createChatbotFromUrl, getEmbedCode, checkAuth, logout, createPortalSession, updateChatbot, getSafeErrorMessage, getUserFacingFetchError, logClientIssue, isApiError, getSubscriptionStatusFromApi, type Chatbot, type SubscriptionStatus } from '@/lib/api';
import { copyTextToClipboard } from '@/lib/clipboard';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Book, Plus, X, Eye, Code, Copy, CheckCircle, Crown, LogOut, CreditCard, User } from 'lucide-react';
import ChatbotCreationLoader from '@/components/ChatbotCreationLoader';
import CreateChatbotFromWebsiteForm from '@/components/CreateChatbotFromWebsiteForm';
import PaywallModal from '@/components/PaywallModal';
import ThemePicker, { type PastelTheme, PASTEL_PRESETS } from '@/components/ThemePicker';
import AvatarPicker from '@/components/AvatarPicker';
import { useSetDashboardNav } from '@/context/DashboardNavContext';
import { type AvatarId } from '@/lib/api';
import { isBillingEnabledFromEnv, paymentActionsAvailableFromApi } from '@/lib/billing-config';

export default function Dashboard() {
  const router = useRouter();
  const [chatbots, setChatbots] = useState<Chatbot[]>([]);
  const [loading, setLoading] = useState(true);
  const [authenticated, setAuthenticated] = useState(false);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [selectedChatbot, setSelectedChatbot] = useState<Chatbot | null>(null);
  const [embedCode, setEmbedCode] = useState('');
  const [embedCopyFeedback, setEmbedCopyFeedback] = useState<'idle' | 'success' | 'error'>('idle');
  const [subscriptionStatus, setSubscriptionStatus] = useState<SubscriptionStatus | null>(null);
  const [showUpgradeModal, setShowUpgradeModal] = useState(false);
  const [upgradeMessage, setUpgradeMessage] = useState('');
  const [paywallFeature, setPaywallFeature] = useState<'chatbot-limit' | 'integration-script' | 'advanced-features' | 'general'>('general');
  const [portalLoading, setPortalLoading] = useState(false);
  const [jesusTogglingId, setJesusTogglingId] = useState<number | null>(null);
  const [themeApplyingId, setThemeApplyingId] = useState<number | null>(null);
  const [avatarApplyingId, setAvatarApplyingId] = useState<number | null>(null);

  const [creating, setCreating] = useState(false);
  const [createFormError, setCreateFormError] = useState('');
  /** Non-auth failures loading the list (403/5xx/network): keep session and let user retry */
  const [chatbotsLoadError, setChatbotsLoadError] = useState<string | null>(null);

  const offerPaymentUi = (status: SubscriptionStatus | null) =>
    status ? paymentActionsAvailableFromApi(status) : isBillingEnabledFromEnv();

  useEffect(() => {
    loadChatbots();
    loadSubscriptionStatus();
  }, []);

  // Redirect to login if not authenticated (use useEffect to avoid showing modal)
  // This must be before early returns to maintain hook order
  useEffect(() => {
    if (!loading && !authenticated) {
      router.replace('/login');
    }
  }, [loading, authenticated, router]);

  /** Fetch subscription from API for plan limits and embed access (canUseChatbot); UI styling differs in preview vs full access.
   *  Security: subscription is from authenticated API only; embed access is enforced by backend on GET /embed. */
  const loadSubscriptionStatus = async (chatbotCountOverride?: number) => {
    try {
      const api = await getSubscriptionStatusFromApi();
      const canUse = !!api?.canUseChatbot;
      const count = typeof chatbotCountOverride === 'number' && chatbotCountOverride >= 0 ? chatbotCountOverride : chatbots.length;
      setSubscriptionStatus({
        isPreviewMode: !canUse,
        canAccessIntegrationScript: canUse,
        maxChatbots: canUse ? 10 : 1,
        currentChatbotCount: count,
        plan: api?.plan,
        billingEnabled: api?.billingEnabled,
        paymentActionsAvailable: api?.paymentActionsAvailable,
      });
    } catch (error: unknown) {
      console.error('Error loading subscription status:', error);
      const fallbackCount = typeof chatbotCountOverride === 'number' && chatbotCountOverride >= 0 ? chatbotCountOverride : chatbots.length;
      setSubscriptionStatus({
        isPreviewMode: true,
        canAccessIntegrationScript: false,
        maxChatbots: 1,
        currentChatbotCount: fallbackCount,
        plan: undefined,
        billingEnabled: undefined,
        paymentActionsAvailable: undefined,
      });
    }
  };

  const loadChatbots = async () => {
    setChatbotsLoadError(null);
    try {
      const data = await getAllChatbots();
      setChatbots(data);
      setAuthenticated(true);

      // Refetch subscription from API so embed button styling matches access tier
      const api = await getSubscriptionStatusFromApi();
      const canUse = !!api?.canUseChatbot;
      setSubscriptionStatus({
        isPreviewMode: !canUse,
        canAccessIntegrationScript: canUse,
        maxChatbots: canUse ? 10 : 1,
        currentChatbotCount: data.length,
        plan: api?.plan,
        billingEnabled: api?.billingEnabled,
        paymentActionsAvailable: api?.paymentActionsAvailable,
      });

      // If user has no chatbots, redirect to onboarding
      if (data.length === 0) {
        router.push('/onboarding');
        return;
      }
    } catch (error: unknown) {
      console.error('Error loading chatbots:', error);
      const status = isApiError(error) ? error.status : undefined;
      if (status === 401) {
        setAuthenticated(false);
      } else {
        // 403, 402, 5xx, network: do not force logout — scan limits and transient errors are not auth failures
        try {
          const authResult = await checkAuth();
          if (!authResult.authenticated) {
            setAuthenticated(false);
          } else {
            setAuthenticated(true);
            setChatbotsLoadError(
              getUserFacingFetchError(error, 'Could not load your chatbots. Please try again.')
            );
          }
        } catch {
          setAuthenticated(false);
        }
      }
    } finally {
      setLoading(false);
    }
  };

  const handleGetEmbedCode = async (chatbotId: number) => {
    try {
      const code = await getEmbedCode(chatbotId);
      setEmbedCode(code);
      setSelectedChatbot(chatbots.find(c => c.id === chatbotId) || null);
    } catch (error: unknown) {
      console.error('Error getting embed code:', error);
      const msg = getSafeErrorMessage(error, 'Failed to get embed code. Please try again.');
      if (msg.includes('Upgrade') && offerPaymentUi(subscriptionStatus)) {
        setUpgradeMessage(msg);
        setPaywallFeature('integration-script');
        setShowUpgradeModal(true);
      } else if (msg.includes('Upgrade')) {
        alert(getSafeErrorMessage(error, 'Embed is not available for your account right now.'));
      } else {
        alert(msg);
      }
    }
  };

  const handleCreateFromUrl = async (canonicalUrl: string) => {
    setCreateFormError('');
    setCreating(true);

    try {
      const newChatbot = await createChatbotFromUrl(canonicalUrl);
      setChatbots([...chatbots, newChatbot]);
      setShowCreateForm(false);
      setCreating(false);
      loadSubscriptionStatus(chatbots.length + 1);
    } catch (error: unknown) {
      console.error('Error creating chatbot:', error);
      setCreating(false);
      const msg = getSafeErrorMessage(error, 'Failed to create chatbot. Please try again.');

      if (
        isApiError(error) &&
        (error.status === 402 || error.upgradeRequired === true || error.websiteTooLarge === true)
      ) {
        setUpgradeMessage(
          msg ||
            'This site has more pages than we can scan at once (up to 500 per scan). Try a smaller section or subdomain.'
        );
        setPaywallFeature('general');
        if (offerPaymentUi(subscriptionStatus)) {
          setShowUpgradeModal(true);
        } else {
          setCreateFormError(
            msg ||
              'This site has more pages than we can scan at once (up to 500 per scan). Try a smaller section or subdomain.'
          );
        }
        return;
      }
      if (msg.includes('limit') || msg.includes('Upgrade')) {
        setUpgradeMessage(msg || 'One chatbot per account limit reached. Upgrade to create more.');
        setPaywallFeature('chatbot-limit');
        if (offerPaymentUi(subscriptionStatus)) {
          setShowUpgradeModal(true);
        } else {
          setCreateFormError(msg || 'You have reached the limit for your account.');
        }
        return;
      }
      setCreateFormError(msg);
    }
  };

  const handleLogout = async () => {
    try {
      await logout();

      // App logout only (session/JWT). Do NOT auto-log the user out of Google.
      router.replace('/');
      window.location.href = '/';
    } catch (error: unknown) {
      console.error('Error logging out:', error);
      router.replace('/');
      window.location.href = '/';
    }
  };

  const openSubscription = useCallback(async () => {
    setPortalLoading(true);
    try {
      const returnUrl = typeof window !== 'undefined' ? `${window.location.origin}/dashboard` : undefined;
      const url = await createPortalSession(returnUrl);
      const allowed = (u: string) => {
        try {
          const o = new URL(u);
          return ['billing.stripe.com', 'billing.stripe.dev'].includes(o.hostname);
        } catch {
          return false;
        }
      };
      if (!url || typeof url !== 'string' || !allowed(url)) {
        alert('Invalid billing portal URL. Please try again or contact support.');
        return;
      }
      window.location.href = url;
    } catch (e) {
      const msg = e instanceof Error ? e.message : 'Failed to open billing portal';
      if (!msg.includes('apiKey') && !msg.includes('secret') && !msg.includes('stack')) {
        alert(msg);
      } else {
        alert('Something went wrong. Please try again or contact support.');
      }
    } finally {
      setPortalLoading(false);
    }
  }, []);

  const setNav = useSetDashboardNav();
  useEffect(() => {
    if (!authenticated || loading) {
      setNav(null);
      return;
    }
    const maxBots = subscriptionStatus?.maxChatbots ?? 1;
    const canAddChatbot = chatbots.length < maxBots;
    setNav({
      openSubscription,
      logout: handleLogout,
      toggleCreateForm: () => setShowCreateForm((s) => !s),
      showCreateForm,
      canAddChatbot,
      isPreviewMode: subscriptionStatus?.isPreviewMode ?? true,
      portalLoading,
      showSubscriptionNav: offerPaymentUi(subscriptionStatus),
    });
    return () => setNav(null);
  }, [
    authenticated,
    loading,
    openSubscription,
    showCreateForm,
    chatbots.length,
    subscriptionStatus?.maxChatbots,
    subscriptionStatus?.isPreviewMode,
    subscriptionStatus?.billingEnabled,
    subscriptionStatus?.paymentActionsAvailable,
    portalLoading,
    setNav,
  ]);

  if (loading) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center">
        <Book className="w-16 h-16 text-brown-600 animate-pulse mb-4" strokeWidth={1.5} />
        <div className="text-xl text-brown-700">Loading your chatbots...</div>
      </div>
    );
  }

  if (!authenticated) {
    // Show loading state while redirecting
    return (
      <div className="min-h-screen flex flex-col items-center justify-center">
        <Book className="w-16 h-16 text-brown-600 animate-pulse mb-4" strokeWidth={1.5} />
        <div className="text-xl text-brown-700">Redirecting to login...</div>
      </div>
    );
  }

  return (
    <main className="min-h-screen p-4 sm:p-6 md:p-8">
      <ChatbotCreationLoader isVisible={creating} chatbotName="Your Chatbot" />
      <div className="max-w-4xl mx-auto min-w-0">
        <h1 className="sr-only">Prayer-Chat Dashboard</h1>
        {chatbotsLoadError && (
          <div
            role="alert"
            className="mb-4 p-4 rounded-xl bg-amber-50 border border-amber-200 text-brown-800 flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"
          >
            <p className="text-sm">{chatbotsLoadError}</p>
            <button
              type="button"
              onClick={() => {
                setLoading(true);
                void loadChatbots();
              }}
              className="shrink-0 px-4 py-2 rounded-lg bg-brown-700 text-white text-sm font-medium hover:bg-brown-800 cursor-pointer"
            >
              Retry
            </button>
          </div>
        )}
        {/* Optional Preview Mode badge when nav is in header */}
        {subscriptionStatus?.isPreviewMode && (
          <p className="mb-4 text-xs text-brown-600 font-medium">Preview Mode</p>
        )}

        {/* Mobile overview card (Option A) */}
        <section className="sm:hidden mb-6">
          <div className="rounded-2xl border border-brown-100 bg-brown-50/80 shadow-sm p-4">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="text-xs uppercase tracking-wide text-brown-600 font-semibold">Overview</p>
                <p className="text-brown-900 font-bold text-lg leading-tight">Your Dashboard</p>
                <p className="text-brown-700 text-sm mt-1">
                  {subscriptionStatus?.isPreviewMode
                    ? offerPaymentUi(subscriptionStatus)
                      ? 'Preview mode: embed is locked until subscription is active.'
                      : 'Manage chatbots and copy your embed code.'
                    : 'Manage chatbots and copy your embed code.'}
                </p>
              </div>
              <Link
                href="/account"
                className="flex-shrink-0 inline-flex items-center gap-2 px-3 py-2 rounded-xl bg-white border border-brown-200 text-brown-800 text-sm font-medium"
                aria-label="Open account"
              >
                <User className="w-4 h-4" /> Account
              </Link>
            </div>

            <div className="mt-4 grid grid-cols-3 gap-2">
              <div className="rounded-xl bg-white border border-brown-200 p-3">
                <p className="text-[11px] text-brown-600 font-semibold">Plan</p>
                <p className="text-brown-900 font-bold text-sm truncate">
                  {subscriptionStatus?.plan || (subscriptionStatus?.isPreviewMode ? 'Preview' : 'Active')}
                </p>
              </div>
              <div className="rounded-xl bg-white border border-brown-200 p-3">
                <p className="text-[11px] text-brown-600 font-semibold">Chatbots</p>
                <p className="text-brown-900 font-bold text-sm">
                  {chatbots.length}
                  {typeof subscriptionStatus?.maxChatbots === 'number' ? ` / ${subscriptionStatus.maxChatbots}` : ''}
                </p>
              </div>
              <div className="rounded-xl bg-white border border-brown-200 p-3">
                <p className="text-[11px] text-brown-600 font-semibold">Embed</p>
                <p className="text-brown-900 font-bold text-sm">
                  {subscriptionStatus?.canAccessIntegrationScript ? 'Ready' : 'Locked'}
                </p>
              </div>
            </div>

            <div className="mt-4 grid grid-cols-2 gap-3">
              {(subscriptionStatus ? chatbots.length < subscriptionStatus.maxChatbots : true) ? (
                <button
                  type="button"
                  onClick={() => setShowCreateForm(!showCreateForm)}
                  className="w-full px-4 py-3 rounded-2xl bg-gradient-to-r from-brown-600 to-gold-600 text-white font-semibold flex items-center justify-center gap-2"
                >
                  {showCreateForm ? <X className="w-5 h-5" /> : <Plus className="w-5 h-5" />}
                  {showCreateForm ? 'Cancel' : 'New chatbot'}
                </button>
              ) : null}
              {offerPaymentUi(subscriptionStatus) ? (
                <button
                  type="button"
                  onClick={async () => {
                    setPortalLoading(true);
                    try {
                      const returnUrl = typeof window !== 'undefined' ? `${window.location.origin}/dashboard` : undefined;
                      const url = await createPortalSession(returnUrl);
                      const allowed = (u: string) => {
                        try {
                          const o = new URL(u);
                          return ['billing.stripe.com', 'billing.stripe.dev'].includes(o.hostname);
                        } catch {
                          return false;
                        }
                      };
                      if (!url || typeof url !== 'string' || !allowed(url)) {
                        alert('Invalid billing portal URL. Please try again or contact support.');
                        return;
                      }
                      window.location.href = url;
                    } catch (e) {
                      const msg = e instanceof Error ? e.message : 'Failed to open billing portal';
                      if (!msg.includes('apiKey') && !msg.includes('secret') && !msg.includes('stack')) {
                        alert(msg);
                      } else {
                        alert('Something went wrong. Please try again or contact support.');
                      }
                    } finally {
                      setPortalLoading(false);
                    }
                  }}
                  disabled={portalLoading}
                  className="w-full px-4 py-3 rounded-2xl bg-white border border-brown-200 text-brown-900 font-semibold flex items-center justify-center gap-2 disabled:opacity-60"
                >
                  <CreditCard className="w-5 h-5" />
                  {portalLoading ? 'Opening…' : 'Subscription'}
                </button>
              ) : null}
            </div>
          </div>
        </section>

        {showCreateForm && (
          <motion.div
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            className="bg-brown-50/90 backdrop-blur-sm rounded-2xl shadow p-8 mb-8 border border-brown-100 max-w-xl mx-auto"
          >
            <div className="flex items-center justify-between mb-6">
              <div className="flex items-center gap-2">
                <Book className="w-6 h-6 text-brown-700" />
                <h2 className="text-2xl font-bold text-brown-800">Create New Chatbot</h2>
              </div>
              {subscriptionStatus?.isPreviewMode && (
                <div className="bg-gold-50 border border-gold-200 rounded-lg px-3 py-1.5">
                  <span className="text-sm text-gold-800 font-medium">
                    Preview: 1 chatbot allowed
                  </span>
                </div>
              )}
            </div>
            <CreateChatbotFromWebsiteForm
              variant="dashboard"
              onSubmit={handleCreateFromUrl}
              submitting={creating}
              serverError={createFormError}
              onClearServerError={() => setCreateFormError('')}
            />
          </motion.div>
        )}

        {/* Centered chatbot preview card(s) */}
        <div className="flex flex-col items-center">
          <div className={`w-full max-w-2xl mx-auto ${chatbots.length > 1 ? 'grid grid-cols-1 gap-6 sm:grid-cols-2' : ''}`}>
            {chatbots.map((chatbot) => (
              <motion.div
                key={chatbot.id}
                initial={{ opacity: 0, scale: 0.98 }}
                animate={{ opacity: 1, scale: 1 }}
                className="bg-brown-50/90 backdrop-blur-sm rounded-2xl shadow-sm p-6 hover:shadow transition-all border border-brown-100"
              >
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <Book className="w-5 h-5 text-brown-700 flex-shrink-0" />
                    <h3 className="text-xl font-bold text-brown-800">{chatbot.name}</h3>
                  </div>
                </div>
                <p className="text-brown-700 mb-4">{chatbot.description}</p>

                <div className="space-y-2">
                  <Link
                    href={`/chatbot/${chatbot.id}`}
                    className="flex items-center justify-center gap-2 w-full px-4 py-2.5 bg-brown-100 text-brown-800 rounded-lg hover:bg-brown-200 transition-colors font-medium cursor-pointer"
                  >
                    <Eye className="w-4 h-4" />
                    Preview Chatbot
                  </Link>

                  <button
                    type="button"
                    onClick={() => {
                      handleGetEmbedCode(chatbot.id);
                      setSelectedChatbot(chatbot);
                    }}
                    className={`flex items-center justify-center gap-2 w-full px-4 py-2.5 rounded-lg transition-colors font-medium cursor-pointer ${
                      subscriptionStatus?.isPreviewMode
                        ? 'bg-brown-100 text-brown-600 hover:bg-brown-200'
                        : 'bg-gold-100 text-gold-800 hover:bg-gold-200'
                    }`}
                  >
                    {subscriptionStatus?.isPreviewMode ? (
                      <>
                        <Crown className="w-4 h-4" />
                        Website embed snippet
                      </>
                    ) : (
                      <>
                        <Code className="w-4 h-4" />
                        Get Embed Code
                      </>
                    )}
                  </button>
                </div>
                <div className="mt-3 pt-3 border-t border-brown-200 space-y-1.5">
                  <label className="flex items-center gap-2 cursor-pointer select-none">
                    <input
                      type="checkbox"
                      checked={chatbot.jesusTeachingsEnabled === true}
                      disabled={jesusTogglingId === chatbot.id}
                      onChange={async () => {
                        setJesusTogglingId(chatbot.id);
                        try {
                          const updated = await updateChatbot(chatbot.id, {
                            ...chatbot,
                            jesusTeachingsEnabled: !chatbot.jesusTeachingsEnabled,
                          });
                          setChatbots((prev) => prev.map((c) => (c.id === updated.id ? updated : c)));
                        } finally {
                          setJesusTogglingId(null);
                        }
                      }}
                      className="w-4 h-4 rounded border-brown-300 text-gold-600 focus:ring-gold-500 cursor-pointer flex-shrink-0 mt-0.5"
                    />
                    <span className="text-sm font-medium text-brown-700 leading-5">Include &quot;What Jesus Would Say&quot;</span>
                  </label>
                  {chatbot.bibleVerse && (
                    <p className="text-xs text-brown-600 italic pl-0 line-clamp-2" title={chatbot.bibleVerse}>
                      {chatbot.bibleVerse}
                    </p>
                  )}
                </div>

                <div className="mt-4 pt-4 border-t border-brown-200">
                  <AvatarPicker
                    currentAvatarId={chatbot.avatarId ?? ''}
                    onSelect={async (avatarId: '' | AvatarId) => {
                      setAvatarApplyingId(chatbot.id);
                      try {
                        const payload = {
                          ...chatbot,
                          name: (chatbot.name && chatbot.name.trim()) || 'Chatbot',
                          websiteUrl: (chatbot.websiteUrl && chatbot.websiteUrl.trim()) || 'https://example.com',
                          avatarId: avatarId || null,
                        };
                        const updated = await updateChatbot(chatbot.id, payload);
                        setChatbots((prev) => prev.map((c) => (c.id === updated.id ? updated : c)));
                      } catch (err) {
                        logClientIssue('dashboard.avatar.save', err);
                        alert(getUserFacingFetchError(err, 'Failed to save avatar. Please try again.'));
                      } finally {
                        setAvatarApplyingId(null);
                      }
                    }}
                    disabled={avatarApplyingId === chatbot.id}
                  />
                  <ThemePicker
                    currentBrandingConfig={chatbot.brandingConfig ?? '{}'}
                    applying={themeApplyingId === chatbot.id}
                    onApply={async (theme: PastelTheme) => {
                      if (!PASTEL_PRESETS.some((p) => p.primaryColor === theme.primaryColor && p.secondaryColor === theme.secondaryColor)) {
                        return;
                      }
                      setThemeApplyingId(chatbot.id);
                      try {
                        const merged: Record<string, string> = {};
                        if (chatbot.brandingConfig) {
                          try {
                            const existing = JSON.parse(chatbot.brandingConfig) as Record<string, unknown>;
                            if (typeof existing.fontFamily === 'string') merged.fontFamily = existing.fontFamily;
                          } catch {
                            /* ignore */
                          }
                        }
                        merged.primaryColor = theme.primaryColor;
                        merged.secondaryColor = theme.secondaryColor;
                        if (theme.borderRadius) merged.borderRadius = theme.borderRadius;
                        // Backend @Valid requires non-blank name and websiteUrl. Blank causes 400 and theme is not saved.
                        const payload = {
                          ...chatbot,
                          name: (chatbot.name && chatbot.name.trim()) || 'Chatbot',
                          websiteUrl: (chatbot.websiteUrl && chatbot.websiteUrl.trim()) || 'https://example.com',
                          brandingConfig: JSON.stringify(merged),
                        };
                        const updated = await updateChatbot(chatbot.id, payload);
                        setChatbots((prev) => prev.map((c) => (c.id === updated.id ? updated : c)));
                      } catch (err) {
                        logClientIssue('dashboard.theme.save', err);
                        alert(getUserFacingFetchError(err, 'Failed to save theme. Please try again.'));
                      } finally {
                        setThemeApplyingId(null);
                      }
                    }}
                  />
                </div>
              </motion.div>
            ))}
          </div>
        </div>

        {chatbots.length === 0 && !showCreateForm && (
          <div className="text-center py-12 sm:py-16 max-w-xl mx-auto px-2 min-w-0">
            <Book className="w-16 h-16 sm:w-20 sm:h-20 text-brown-400 mx-auto mb-4" strokeWidth={1.5} />
            <p className="text-lg sm:text-xl text-brown-700 mb-4">No chatbots yet</p>
            <button
              type="button"
              onClick={() => setShowCreateForm(true)}
              className="w-fit max-w-xs mx-auto px-6 py-3 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-xl font-medium hover:shadow-lg transition-all inline-flex items-center justify-center gap-2 cursor-pointer"
            >
              <Plus className="w-5 h-5 flex-shrink-0" />
              Create Your First Chatbot
            </button>
          </div>
        )}

        {embedCode && selectedChatbot && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50 overflow-y-auto"
            onClick={() => setEmbedCode('')}
          >
            <div
              className="bg-brown-50 rounded-2xl p-6 sm:p-8 max-w-2xl w-full min-w-0 max-h-[min(90vh,40rem)] overflow-y-auto border border-brown-200 shadow-lg my-auto"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-center gap-2 mb-2">
                <Code className="w-6 h-6 text-brown-700 flex-shrink-0" />
                <h3 className="text-xl sm:text-2xl font-bold text-brown-800 truncate min-w-0">Embed code for {selectedChatbot.name}</h3>
              </div>
              <p className="text-brown-700 text-sm mb-3">
                Paste this snippet just before the closing <code className="bg-brown-200 px-1 rounded">&lt;/body&gt;</code> on your website. A chat button will appear so visitors can ask questions—like planting a small seed of encouragement on your site.
              </p>
              <pre className="bg-brown-100 p-4 rounded-lg overflow-x-auto mb-4 border border-brown-300 text-brown-900 text-sm sm:text-base">
                <code>{embedCode}</code>
              </pre>
              <div className="flex flex-col-reverse sm:flex-row gap-3 sm:gap-4">
                <button
                  type="button"
                  onClick={async () => {
                    const ok = await copyTextToClipboard(embedCode);
                    setEmbedCopyFeedback(ok ? 'success' : 'error');
                    setTimeout(() => setEmbedCopyFeedback('idle'), 2000);
                  }}
                  className="flex-1 min-w-0 px-4 py-2 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-lg hover:shadow-lg transition-all flex items-center justify-center gap-2"
                >
                  {embedCopyFeedback === 'success' ? <CheckCircle className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
                  {embedCopyFeedback === 'success' ? 'Copied!' : embedCopyFeedback === 'error' ? 'Copy failed' : 'Copy code'}
                </button>
                <button
                  onClick={() => setEmbedCode('')}
                  className="w-full sm:w-auto px-4 py-2 bg-brown-200 text-brown-800 rounded-lg hover:bg-brown-300 transition-colors flex items-center justify-center gap-2"
                >
                  <X className="w-4 h-4" />
                  Close
                </button>
              </div>
            </div>
          </motion.div>
        )}

        <PaywallModal
          isOpen={showUpgradeModal}
          onClose={() => setShowUpgradeModal(false)}
          title={upgradeMessage ? undefined : undefined}
          message={upgradeMessage || undefined}
          feature={paywallFeature}
          billingActionsAvailable={offerPaymentUi(subscriptionStatus)}
        />
      </div>
    </main>
  );
}
