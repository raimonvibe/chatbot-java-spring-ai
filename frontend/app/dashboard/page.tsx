'use client';

import { useState, useEffect, useCallback, useRef } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { getAllChatbots, createChatbotFromUrl, getEmbedCode, deleteChatbot, logout, createPortalSession, getSafeErrorMessage, getUserFacingFetchError, isApiError, type Chatbot, type SubscriptionStatus } from '@/lib/api';
import Link from 'next/link';
import { useRouter, usePathname } from 'next/navigation';
import { Book, Plus, X } from 'lucide-react';
import ChatbotCreationLoader from '@/components/ChatbotCreationLoader';
import CreateChatbotFromWebsiteForm from '@/components/CreateChatbotFromWebsiteForm';
import PaywallModal from '@/components/PaywallModal';
import ChatbotCard from '@/components/dashboard/ChatbotCard';
import DeleteChatbotModal from '@/components/dashboard/DeleteChatbotModal';
import EmbedCodeModal from '@/components/dashboard/EmbedCodeModal';
import DashboardMobileOverview from '@/components/dashboard/DashboardMobileOverview';
import { useSetDashboardNav } from '@/context/DashboardNavContext';
import { useRequireAuth } from '@/hooks/useRequireAuth';
import { useSubscription } from '@/hooks/useSubscription';
import { isBillingEnabledFromEnv, paymentActionsAvailableFromApi } from '@/lib/billing-config';

export default function Dashboard() {
  const router = useRouter();
  const pathname = usePathname();
  const prevPathnameRef = useRef<string | null>(null);
  const { authenticated, loading: authLoading, refresh: refreshAuth } = useRequireAuth();
  const [chatbots, setChatbots] = useState<Chatbot[]>([]);
  const [chatbotsLoading, setChatbotsLoading] = useState(true);
  const { status: subscriptionStatus, refresh: refreshSubscription } = useSubscription(chatbots.length);
  const loading = authLoading || chatbotsLoading;
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [selectedChatbot, setSelectedChatbot] = useState<Chatbot | null>(null);
  const [embedCode, setEmbedCode] = useState('');
  const [embedCopyFeedback, setEmbedCopyFeedback] = useState<'idle' | 'success' | 'error'>('idle');
  const [showUpgradeModal, setShowUpgradeModal] = useState(false);
  const [upgradeMessage, setUpgradeMessage] = useState('');
  const [paywallFeature, setPaywallFeature] = useState<'chatbot-limit' | 'integration-script' | 'advanced-features' | 'general'>('general');
  const [portalLoading, setPortalLoading] = useState(false);

  const [creating, setCreating] = useState(false);
  const [createFormError, setCreateFormError] = useState('');
  /** Non-auth failures loading the list (403/5xx/network): keep session and let user retry */
  const [chatbotsLoadError, setChatbotsLoadError] = useState<string | null>(null);
  /** Embed fetch failed: inline banner (no browser alert). */
  const [embedFetchError, setEmbedFetchError] = useState<{
    message: string;
    kind: 'sign-in' | 'generic';
    chatbotId: number;
  } | null>(null);
  const [embedFetchingId, setEmbedFetchingId] = useState<number | null>(null);
  const [chatbotDeletingId, setChatbotDeletingId] = useState<number | null>(null);
  /** In-page delete confirmation (replaces window.confirm). */
  const [deleteConfirmChatbot, setDeleteConfirmChatbot] = useState<Chatbot | null>(null);
  const [deleteFlowError, setDeleteFlowError] = useState<string | null>(null);
  /** True while sending users with zero chatbots to onboarding — avoids empty-state button flash. */
  const [redirectingToOnboarding, setRedirectingToOnboarding] = useState(false);

  const offerPaymentUi = (status: SubscriptionStatus | null) =>
    status ? paymentActionsAvailableFromApi(status) : isBillingEnabledFromEnv();

  useEffect(() => {
    if (authLoading || !authenticated) return;
    void loadChatbots();
  }, [authLoading, authenticated]);

  /** Re-entering dashboard from preview (or any other route) refetches chatbots — pairs with GET cache: no-store. */
  useEffect(() => {
    const prev = prevPathnameRef.current;
    prevPathnameRef.current = pathname;
    if (pathname !== '/dashboard' || loading || !authenticated) return;
    if (prev != null && prev !== '/dashboard') {
      void loadChatbots();
    }
  }, [pathname, loading, authenticated]);

  /** If client navigation stalls, retry onboarding so the user is not stuck on a spinner forever. */
  useEffect(() => {
    if (!redirectingToOnboarding) return;
    const retry = setTimeout(() => {
      router.replace('/onboarding');
    }, 10_000);
    return () => clearTimeout(retry);
  }, [redirectingToOnboarding, router]);

  const loadChatbots = async () => {
    setChatbotsLoadError(null);
    let skipFinishLoading = false;
    try {
      const data = await getAllChatbots();
      setChatbots(data);

      if (data.length === 0) {
        skipFinishLoading = true;
        setRedirectingToOnboarding(true);
        router.replace('/onboarding');
        return;
      }
    } catch (error: unknown) {
      console.error('Error loading chatbots:', error);
      const status = isApiError(error) ? error.status : undefined;
      if (status === 401) {
        await refreshAuth();
      } else {
        setChatbotsLoadError(getUserFacingFetchError(error, 'Could not load your chatbots. Please try again.'));
      }
    } finally {
      if (!skipFinishLoading) {
        setChatbotsLoading(false);
      }
    }
  };

  const handleGetEmbedCode = async (chatbotId: number) => {
    setEmbedFetchError(null);
    setEmbedFetchingId(chatbotId);
    try {
      const code = await getEmbedCode(chatbotId);
      setEmbedCode(code);
      setSelectedChatbot(chatbots.find((c) => c.id === chatbotId) || null);
    } catch (error: unknown) {
      console.error('Error getting embed code:', error);
      const msg = getSafeErrorMessage(error, 'Failed to get embed code. Please try again.');
      if (msg.includes('Upgrade') && offerPaymentUi(subscriptionStatus)) {
        setUpgradeMessage(msg);
        setPaywallFeature('integration-script');
        setShowUpgradeModal(true);
      } else if (msg.includes('Upgrade')) {
        setEmbedFetchError({
          message: getSafeErrorMessage(error, 'Embed is not available for your account right now.'),
          kind: 'generic',
          chatbotId,
        });
      } else {
        const status = isApiError(error) ? error.status : undefined;
        setEmbedFetchError({
          message: msg,
          kind: status === 401 ? 'sign-in' : 'generic',
          chatbotId,
        });
      }
    } finally {
      setEmbedFetchingId(null);
    }
  };

  const closeDeleteConfirmModal = () => {
    if (chatbotDeletingId !== null) return;
    setDeleteConfirmChatbot(null);
    setDeleteFlowError(null);
  };

  const openDeleteConfirmModal = (chatbot: Chatbot) => {
    setDeleteFlowError(null);
    setDeleteConfirmChatbot(chatbot);
    void refreshSubscription(chatbots.length);
  };

  const performConfirmedDelete = async () => {
    const chatbot = deleteConfirmChatbot;
    if (!chatbot) return;
    setChatbotDeletingId(chatbot.id);
    setEmbedFetchError(null);
    try {
      await deleteChatbot(chatbot.id);
      const remaining = chatbots.filter((c) => c.id !== chatbot.id);
      setChatbots(remaining);
      if (selectedChatbot?.id === chatbot.id) {
        setSelectedChatbot(null);
        setEmbedCode('');
      }
      setDeleteConfirmChatbot(null);
      setDeleteFlowError(null);
      await refreshSubscription(remaining.length);
      if (remaining.length === 0) {
        setRedirectingToOnboarding(true);
        router.replace('/onboarding');
      }
    } catch (err: unknown) {
      setDeleteFlowError(getUserFacingFetchError(err, 'Could not delete chatbot. Please try again.'));
    } finally {
      setChatbotDeletingId(null);
    }
  };

  useEffect(() => {
    if (!deleteConfirmChatbot) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key !== 'Escape') return;
      if (chatbotDeletingId !== null) return;
      setDeleteConfirmChatbot(null);
      setDeleteFlowError(null);
    };
    window.addEventListener('keydown', onKeyDown);
    return () => window.removeEventListener('keydown', onKeyDown);
  }, [deleteConfirmChatbot, chatbotDeletingId]);

  useEffect(() => {
    if (!deleteConfirmChatbot) return;
    const prev = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    return () => {
      document.body.style.overflow = prev;
    };
  }, [deleteConfirmChatbot]);

  const handleCreateFromUrl = async (canonicalUrl: string) => {
    if (creating) return; // guard against double submit (duplicate chatbot + double scan cost)
    setCreateFormError('');
    setCreating(true);

    try {
      const newChatbot = await createChatbotFromUrl(canonicalUrl);
      setChatbots([...chatbots, newChatbot]);
      setShowCreateForm(false);
      setCreating(false);
      void refreshSubscription(chatbots.length + 1);
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
    if (!authenticated || loading || redirectingToOnboarding) {
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
    redirectingToOnboarding,
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

  if (loading || redirectingToOnboarding) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center">
        <Book className="w-16 h-16 text-brown-600 animate-pulse mb-4" strokeWidth={1.5} />
        <div className="text-xl text-brown-700">
          {redirectingToOnboarding ? 'Setting up your account...' : 'Loading your chatbots...'}
        </div>
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
      <AnimatePresence>
        {deleteConfirmChatbot && (
          <DeleteChatbotModal
            chatbot={deleteConfirmChatbot}
            subscriptionStatus={subscriptionStatus}
            deleting={chatbotDeletingId === deleteConfirmChatbot.id}
            error={deleteFlowError}
            onClose={closeDeleteConfirmModal}
            onConfirm={() => void performConfirmedDelete()}
          />
        )}
      </AnimatePresence>
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
                setChatbotsLoading(true);
                void loadChatbots();
              }}
              className="shrink-0 px-4 py-2 rounded-lg bg-brown-700 text-white text-sm font-medium hover:bg-brown-800 cursor-pointer"
            >
              Retry
            </button>
          </div>
        )}
        {embedFetchError && (
          <div
            role="alert"
            className="mb-4 p-4 rounded-xl bg-amber-50 border border-amber-200 text-brown-800 flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between"
          >
            <p className="text-sm min-w-0 pr-2">{embedFetchError.message}</p>
            <div className="flex flex-wrap items-center gap-2 shrink-0">
              {embedFetchError.kind === 'sign-in' && (
                <Link
                  href="/login"
                  className="px-4 py-2 rounded-lg bg-brown-700 text-white text-sm font-medium hover:bg-brown-800"
                >
                  Sign in
                </Link>
              )}
              <button
                type="button"
                onClick={() => void handleGetEmbedCode(embedFetchError.chatbotId)}
                className="px-4 py-2 rounded-lg bg-white border border-brown-300 text-brown-800 text-sm font-medium hover:bg-brown-50"
              >
                Try again
              </button>
              <button
                type="button"
                onClick={() => setEmbedFetchError(null)}
                className="px-4 py-2 rounded-lg text-brown-700 text-sm font-medium hover:bg-amber-100/80"
              >
                Dismiss
              </button>
            </div>
          </div>
        )}
        {/* Optional Preview Mode badge when nav is in header */}
        {subscriptionStatus?.isPreviewMode && (
          <p className="mb-4 text-xs text-brown-600 font-medium">Preview Mode</p>
        )}

        <DashboardMobileOverview
          subscriptionStatus={subscriptionStatus}
          chatbots={chatbots}
          showCreateForm={showCreateForm}
          portalLoading={portalLoading}
          onToggleCreateForm={() => setShowCreateForm((s) => !s)}
          onOpenSubscription={() => void openSubscription()}
        />

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
              <ChatbotCard
                key={chatbot.id}
                chatbot={chatbot}
                subscriptionStatus={subscriptionStatus}
                embedFetchingId={embedFetchingId}
                chatbotDeletingId={chatbotDeletingId}
                onGetEmbedCode={(id) => void handleGetEmbedCode(id)}
                onDelete={openDeleteConfirmModal}
                onUpdated={(updated) =>
                  setChatbots((prev) => prev.map((c) => (c.id === updated.id ? updated : c)))
                }
                onLoadError={setChatbotsLoadError}
              />
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
          <EmbedCodeModal
            chatbot={selectedChatbot}
            embedCode={embedCode}
            copyFeedback={embedCopyFeedback}
            onCopyFeedback={setEmbedCopyFeedback}
            onClose={() => {
              setEmbedCode('');
              setEmbedFetchError(null);
            }}
          />
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
