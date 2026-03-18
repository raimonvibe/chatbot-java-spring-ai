'use client';

import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { getAllChatbots, createChatbotFromUrl, analyzeWebsite, getEmbedCode, deleteChatbot, deleteAllChatbots, checkAuth, logout, createPortalSession, updateChatbot, getSafeErrorMessage, isApiError, getSubscriptionStatusFromApi, type Chatbot, type SubscriptionStatus } from '@/lib/api';
import { copyTextToClipboard } from '@/lib/clipboard';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Book, Plus, X, Eye, Code, Copy, CheckCircle, Crown, Sparkles, Trash2, LogOut, CreditCard, User } from 'lucide-react';
import ChatbotCreationLoader from '@/components/ChatbotCreationLoader';
import PaywallModal from '@/components/PaywallModal';
import ThemePicker, { type PastelTheme } from '@/components/ThemePicker';

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

  const [websiteUrl, setWebsiteUrl] = useState('');
  const [creating, setCreating] = useState(false);
  const [analyzingChatbotId, setAnalyzingChatbotId] = useState<number | null>(null);

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

  /** Fetch real subscription from API so dashboard shows "Get Embed Code" when user has paid (canUseChatbot).
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
      });
    }
  };

  const loadChatbots = async () => {
    try {
      const data = await getAllChatbots();
      setChatbots(data);
      setAuthenticated(true);

      // Refetch subscription from API so embed button shows correctly (Get Embed Code vs Upgrade)
      const api = await getSubscriptionStatusFromApi();
      const canUse = !!api?.canUseChatbot;
      setSubscriptionStatus({
        isPreviewMode: !canUse,
        canAccessIntegrationScript: canUse,
        maxChatbots: canUse ? 10 : 1,
        currentChatbotCount: data.length,
        plan: api?.plan,
      });

      // If user has no chatbots, redirect to onboarding
      if (data.length === 0) {
        router.push('/onboarding');
        return;
      }
    } catch (error: unknown) {
      console.error('Error loading chatbots:', error);
      const status = isApiError(error) ? error.status : undefined;
      if (status === 401 || status === 0 || status === undefined) {
        // 401 = Unauthorized, 0 or undefined = Network/CORS error
        setAuthenticated(false);
        // Don't redirect automatically - let user click the button
        // This prevents redirect loops
      } else {
        // Other errors (500, etc.) - still show as unauthenticated
        setAuthenticated(false);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleAnalyzeWebsite = async (chatbotId: number, websiteUrl: string) => {
    setAnalyzingChatbotId(chatbotId);
    try {
      await analyzeWebsite(chatbotId, websiteUrl);
      // Website analysis started; Christian content will be enabled automatically when ready
    } catch (error: unknown) {
      console.error('Error analyzing website:', error);
      if (isApiError(error) && (error.status === 402 || error.upgradeRequired)) {
        setUpgradeMessage(getSafeErrorMessage(error, 'Website analysis limit reached. Upgrade to analyze more.'));
        setPaywallFeature('general');
        setShowUpgradeModal(true);
      } else {
        alert(getSafeErrorMessage(error, 'Failed to analyze website. Please try again.'));
      }
    } finally {
      setAnalyzingChatbotId(null);
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
      if (msg.includes('Upgrade')) {
        setUpgradeMessage(msg);
        setPaywallFeature('integration-script');
        setShowUpgradeModal(true);
      } else {
        alert(msg);
      }
    }
  };

  const handleCreateChatbot = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreating(true);

    try {
      if (!websiteUrl.trim()) {
        alert('Please enter a website URL');
        setCreating(false);
        return;
      }

      const newChatbot = await createChatbotFromUrl(websiteUrl.trim());
      setChatbots([...chatbots, newChatbot]);
      setWebsiteUrl('');
      setShowCreateForm(false);
      setCreating(false);
      // Refetch subscription so "Get Embed Code" appears if user has paid (e.g. just created first chatbot after payment)
      loadSubscriptionStatus(chatbots.length + 1);
    } catch (error: unknown) {
      console.error('Error creating chatbot:', error);
      setCreating(false);
      const msg = getSafeErrorMessage(error, 'Failed to create chatbot. Please try again.');

      if (isApiError(error) && (error.status === 402 || error.upgradeRequired)) {
        setUpgradeMessage(msg || 'Website too large for preview mode. Upgrade to continue.');
        setPaywallFeature('general');
        setShowUpgradeModal(true);
        return;
      }
      if (msg.includes('limit') || msg.includes('Upgrade')) {
        setUpgradeMessage(msg || 'One chatbot per account limit reached. Upgrade to create more.');
        setPaywallFeature('chatbot-limit');
        setShowUpgradeModal(true);
      } else {
        setUpgradeMessage(msg);
        setShowUpgradeModal(true);
      }
    }
  };

  const handleDeleteChatbot = async (chatbotId: number, chatbotName: string) => {
    if (!confirm(`Are you sure you want to delete "${chatbotName}"? This action cannot be undone.`)) {
      return;
    }

    try {
      await deleteChatbot(chatbotId);
      setChatbots(chatbots.filter(c => c.id !== chatbotId));
      loadSubscriptionStatus(chatbots.length - 1);
    } catch (error: unknown) {
      console.error('Error deleting chatbot:', error);
    }
  };

  const handleDeleteAllChatbots = async () => {
    if (!confirm(`Are you sure you want to delete ALL ${chatbots.length} chatbot(s)? This action cannot be undone and will reset your testing environment.`)) {
      return;
    }

    try {
      const result = await deleteAllChatbots();
      setChatbots([]);
      loadSubscriptionStatus(0);
      alert(`Successfully deleted ${result.deletedCount} chatbot(s).`);
    } catch (error: unknown) {
      console.error('Error deleting all chatbots:', error);
      alert(getSafeErrorMessage(error, 'Failed to delete all chatbots. Please try again.'));
    }
  };

  const handleLogout = async () => {
    try {
      const result = await logout();
      
      // Redirect to Google logout to clear OAuth session
      if (result.googleLogoutUrl) {
        // Open Google logout in new window, then redirect
        window.open(result.googleLogoutUrl, '_blank');
      }
      
      // Redirect to login page
      router.push('/login');
      
      // Force reload to clear all state
      window.location.href = '/login';
    } catch (error: unknown) {
      console.error('Error logging out:', error);
      router.push('/login');
      window.location.href = '/login';
    }
  };

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
        {/* Responsive top navbar: title full-width on mobile, nav wraps */}
        <nav className="flex flex-col gap-4 mb-6 pb-4 border-b border-brown-200">
          <div className="flex items-center gap-3 min-w-0">
            <Book className="w-8 h-8 text-brown-700 flex-shrink-0" strokeWidth={1.5} />
            <div className="min-w-0">
              <h1 className="text-xl sm:text-2xl md:text-3xl font-bold text-brown-800 truncate">
                Prayer-Chat Dashboard
              </h1>
              {subscriptionStatus?.isPreviewMode && (
                <span className="text-xs text-brown-600 font-medium">Preview Mode</span>
              )}
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-2 sm:gap-4 md:gap-6 text-sm font-medium">
            {/* On mobile, hide the redundant "Dashboard" text link to keep the nav compact. */}
            <Link
              href="/dashboard"
              className="hidden sm:inline-flex text-brown-700 hover:text-brown-900 transition-colors whitespace-nowrap py-1"
            >
              Dashboard
            </Link>

            {/* Mobile-first: icon button with larger tap target; label on sm+ */}
            <Link
              href="/account"
              className="inline-flex items-center gap-1.5 whitespace-nowrap px-3 py-2 rounded-xl bg-brown-100/70 border border-brown-200 text-brown-800 hover:bg-brown-100 hover:text-brown-900 transition-colors"
              aria-label="Account"
            >
              <User className="w-4 h-4 flex-shrink-0" />
              <span className="hidden sm:inline">Account</span>
            </Link>
            <button
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
              className="inline-flex items-center gap-1.5 disabled:opacity-50 whitespace-nowrap px-3 py-2 rounded-xl bg-brown-100/70 border border-brown-200 text-brown-800 hover:bg-brown-100 hover:text-brown-900 transition-colors"
              aria-label="Subscription"
            >
              <CreditCard className="w-4 h-4 flex-shrink-0" />
              <span className="hidden sm:inline">{portalLoading ? 'Opening…' : 'Subscription'}</span>
            </button>
            {chatbots.length > 0 || showCreateForm ? (
              <button
                onClick={() => setShowCreateForm(!showCreateForm)}
                className="inline-flex items-center gap-1.5 whitespace-nowrap px-3 py-2 rounded-lg bg-gradient-to-r from-brown-600 to-gold-600 text-white hover:from-brown-700 hover:to-gold-700 transition-all flex-shrink-0 min-w-0"
                aria-label={showCreateForm ? 'Cancel' : 'New Chatbot'}
              >
                {showCreateForm ? <X className="w-4 h-4" /> : <Plus className="w-4 h-4" />}
                <span className="hidden sm:inline">{showCreateForm ? 'Cancel' : 'New Chatbot'}</span>
              </button>
            ) : null}
            <button
              onClick={handleLogout}
              className="inline-flex items-center gap-1.5 whitespace-nowrap px-3 py-2 rounded-xl bg-brown-100/70 border border-brown-200 text-brown-800 hover:bg-brown-100 hover:text-brown-900 transition-colors"
              title="Log out"
              aria-label="Log out"
            >
              <LogOut className="w-4 h-4 flex-shrink-0" />
              <span>Logout</span>
            </button>
            {chatbots.length > 0 && subscriptionStatus?.isPreviewMode && (
              <button
                onClick={handleDeleteAllChatbots}
                className="text-red-600 hover:text-red-700 text-xs whitespace-nowrap py-1"
                title="Delete all chatbots (preview only)"
              >
                Delete All
              </button>
            )}
          </div>
        </nav>

        {/* Mobile overview card (Option A) */}
        <section className="sm:hidden mb-6">
          <div className="rounded-2xl border border-brown-300 bg-brown-50/80 shadow-sm p-4">
            <div className="flex items-start justify-between gap-3">
              <div className="min-w-0">
                <p className="text-xs uppercase tracking-wide text-brown-600 font-semibold">Overview</p>
                <p className="text-brown-900 font-bold text-lg leading-tight">Your Dashboard</p>
                <p className="text-brown-700 text-sm mt-1">
                  {subscriptionStatus?.isPreviewMode
                    ? 'Preview mode: embed is locked until subscription is active.'
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
              {chatbots.length > 0 || showCreateForm ? (
                <button
                  type="button"
                  onClick={() => setShowCreateForm(!showCreateForm)}
                  className="w-full px-4 py-3 rounded-2xl bg-gradient-to-r from-brown-600 to-gold-600 text-white font-semibold flex items-center justify-center gap-2"
                >
                  {showCreateForm ? <X className="w-5 h-5" /> : <Plus className="w-5 h-5" />}
                  {showCreateForm ? 'Cancel' : 'New chatbot'}
                </button>
              ) : null}
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
            </div>
          </div>
        </section>

        {showCreateForm && (
          <motion.div
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            className="bg-brown-50/90 backdrop-blur-sm rounded-2xl shadow-xl p-8 mb-8 border border-brown-200 max-w-xl mx-auto"
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
            <form onSubmit={handleCreateChatbot} className="space-y-6">
              <div>
                <label htmlFor="websiteUrl" className="block text-sm font-medium mb-2 text-brown-800">
                  Enter your website URL
                </label>
                <input
                  id="websiteUrl"
                  type="text"
                  value={websiteUrl}
                  onChange={(e) => setWebsiteUrl(e.target.value)}
                  placeholder="example.com or https://example.com"
                  className="w-full px-4 py-3 border border-brown-300 rounded-lg focus:ring-2 focus:ring-brown-500 focus:border-transparent bg-white text-brown-900 text-lg"
                  disabled={creating}
                  required
                />
                <p className="text-sm text-brown-600 mt-2">
                  We'll analyze your website and create a chatbot that understands your content.
                  Christian values are pre-configured by default.
                </p>
              </div>

              <button
                type="submit"
                disabled={creating || !websiteUrl.trim()}
                className="w-full px-6 py-3 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-xl font-medium disabled:opacity-50 hover:shadow-lg transition-all flex items-center justify-center gap-2"
              >
                <CheckCircle className="w-5 h-5" /> Create My Chatbot
              </button>
            </form>
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
                className="bg-brown-50/90 backdrop-blur-sm rounded-2xl shadow-lg p-6 hover:shadow-xl transition-all border border-brown-200"
              >
                <div className="flex items-center justify-between mb-3">
                  <div className="flex items-center gap-2">
                    <Book className="w-5 h-5 text-brown-700 flex-shrink-0" />
                    <h3 className="text-xl font-bold text-brown-800">{chatbot.name}</h3>
                  </div>
                  <button
                    onClick={() => handleDeleteChatbot(chatbot.id, chatbot.name)}
                    className="p-2 text-red-600 hover:bg-red-50 rounded-lg transition-colors cursor-pointer"
                    title="Delete chatbot"
                    type="button"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
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
                        Upgrade for Embed Code
                      </>
                    ) : (
                      <>
                        <Code className="w-4 h-4" />
                        Get Embed Code
                      </>
                    )}
                  </button>
                  <button
                    type="button"
                    onClick={() => handleAnalyzeWebsite(chatbot.id, chatbot.websiteUrl ?? '')}
                    disabled={analyzingChatbotId !== null}
                    className="flex items-center justify-center gap-2 w-full px-4 py-2.5 rounded-lg transition-colors font-medium bg-gradient-to-r from-brown-100 to-gold-100 text-brown-800 hover:from-brown-200 hover:to-gold-200 border border-brown-200 disabled:opacity-70 cursor-pointer"
                  >
                    <Sparkles className="w-4 h-4" />
                    {analyzingChatbotId === chatbot.id ? 'Analyzing…' : 'Analyze website'}
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
                      className="w-4 h-4 rounded border-brown-300 text-gold-600 focus:ring-gold-500 cursor-pointer"
                    />
                    <span className="text-sm font-medium text-brown-700">Include &quot;What Jesus Would Say&quot;</span>
                  </label>
                  {chatbot.bibleVerse && (
                    <p className="text-xs text-brown-600 italic pl-6 line-clamp-2" title={chatbot.bibleVerse}>
                      {chatbot.bibleVerse}
                    </p>
                  )}
                </div>

                <div className="mt-4 pt-4 border-t border-brown-200">
                  <ThemePicker
                    currentBrandingConfig={chatbot.brandingConfig ?? '{}'}
                    applying={themeApplyingId === chatbot.id}
                    onApply={async (theme: PastelTheme) => {
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
                        // Send full entity so backend @Valid passes (name, websiteUrl required); only brandingConfig is changed; values are preset-only (no user input).
                        const updated = await updateChatbot(chatbot.id, {
                          ...chatbot,
                          brandingConfig: JSON.stringify(merged),
                        });
                        setChatbots((prev) => prev.map((c) => (c.id === updated.id ? updated : c)));
                      } catch (err) {
                        console.error('Failed to apply theme:', err);
                        alert(getSafeErrorMessage(err, 'Failed to save theme. Please try again.'));
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
              className="w-full max-w-sm mx-auto px-6 py-3 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-xl font-medium hover:shadow-lg transition-all inline-flex items-center justify-center gap-2 cursor-pointer"
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
              className="bg-brown-50 rounded-2xl p-6 sm:p-8 max-w-2xl w-full min-w-0 max-h-[min(90vh,40rem)] overflow-y-auto border-2 border-brown-300 shadow-2xl my-auto"
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
        />
      </div>
    </main>
  );
}
