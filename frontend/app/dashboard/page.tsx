'use client';

import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { getAllChatbots, createChatbotFromUrl, analyzeWebsite, getEmbedCode, deleteChatbot, deleteAllChatbots, checkAuth, logout, type Chatbot, type SubscriptionStatus } from '@/lib/api';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Book, Plus, X, Eye, Code, Copy, CheckCircle, Crown, Sparkles, Trash2, LogOut } from 'lucide-react';
import ChatbotCreationLoader from '@/components/ChatbotCreationLoader';
import ChristianContentAnalysisComponent from '@/components/ChristianContentAnalysis';
import PaywallModal from '@/components/PaywallModal';

export default function Dashboard() {
  const router = useRouter();
  const [chatbots, setChatbots] = useState<Chatbot[]>([]);
  const [loading, setLoading] = useState(true);
  const [authenticated, setAuthenticated] = useState(false);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [selectedChatbot, setSelectedChatbot] = useState<Chatbot | null>(null);
  const [embedCode, setEmbedCode] = useState('');
  const [subscriptionStatus, setSubscriptionStatus] = useState<SubscriptionStatus | null>(null);
  const [showUpgradeModal, setShowUpgradeModal] = useState(false);
  const [upgradeMessage, setUpgradeMessage] = useState('');
  const [paywallFeature, setPaywallFeature] = useState<'chatbot-limit' | 'integration-script' | 'advanced-features' | 'general'>('general');
  const [showAnalysisForChatbot, setShowAnalysisForChatbot] = useState<number | null>(null);

  const [websiteUrl, setWebsiteUrl] = useState('');
  const [creating, setCreating] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);

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

  const loadSubscriptionStatus = async () => {
    try {
      // Determine preview mode based on chatbot count and embed access
      const status: SubscriptionStatus = {
        isPreviewMode: chatbots.length >= 1, // If user has 1 chatbot, likely preview mode
        canAccessIntegrationScript: false, // Will be determined when trying to access embed
        maxChatbots: 1,
        currentChatbotCount: chatbots.length,
      };
      setSubscriptionStatus(status);
    } catch (error) {
      console.error('Error loading subscription status:', error);
    }
  };

  const loadChatbots = async () => {
    try {
      const data = await getAllChatbots();
      setChatbots(data);
      setAuthenticated(true);
      
      // If user has no chatbots, redirect to onboarding
      if (data.length === 0) {
        router.push('/onboarding');
        return;
      }
      
      // Update subscription status after loading chatbots
      setSubscriptionStatus({
        isPreviewMode: data.length >= 1, // Heuristic: if user has 1 chatbot, likely preview mode
        canAccessIntegrationScript: false,
        maxChatbots: 1,
        currentChatbotCount: data.length,
      });
    } catch (error) {
      console.error('Error loading chatbots:', error);
      // If unauthorized (401) or network error (CORS), show login prompt
      const status = (error as any).status;
      if (status === 401 || status === 0 || !status) {
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
    setAnalyzing(true);
    try {
      await analyzeWebsite(chatbotId, websiteUrl);
      // Website analysis started silently - no popup needed
    } catch (error) {
      console.error('Error analyzing website:', error);
      // Error is logged, no popup needed
    } finally {
      setAnalyzing(false);
    }
  };

  const handleGetEmbedCode = async (chatbotId: number) => {
    try {
      const code = await getEmbedCode(chatbotId);
      setEmbedCode(code);
      setSelectedChatbot(chatbots.find(c => c.id === chatbotId) || null);
    } catch (error: any) {
      console.error('Error getting embed code:', error);
      // Check if it's a payment required error
      if (error.message && error.message.includes('Upgrade')) {
        setUpgradeMessage(error.message);
        setPaywallFeature('integration-script');
        setShowUpgradeModal(true);
      } else {
        alert(error.message || 'Failed to get embed code. Please try again.');
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

      // Keep loader visible for a moment to show completion
      await new Promise(resolve => setTimeout(resolve, 500));

      setChatbots([...chatbots, newChatbot]);
      setWebsiteUrl('');
      setShowCreateForm(false);
      setCreating(false); // Hide loader only after successful creation

      // Auto-analyze website if URL provided (after loader is hidden)
      if (websiteUrl.trim()) {
        handleAnalyzeWebsite(newChatbot.id, websiteUrl.trim());
      }
    } catch (error: any) {
      console.error('Error creating chatbot:', error);
      setCreating(false); // Hide loader on error
      
      // Check if it's a payment required error (402) - website size limit
      if (error.status === 402 || error.upgradeRequired) {
        setUpgradeMessage(error.message || 'Website too large for preview mode. Upgrade to continue.');
        setPaywallFeature('general');
        setShowUpgradeModal(true);
        return;
      }
      
      // Check if it's a limit reached error
      if (error.message && (error.message.includes('limit') || error.message.includes('Upgrade'))) {
        setUpgradeMessage(error.message || 'One chatbot per account limit reached. Upgrade to create more.');
        setPaywallFeature('chatbot-limit');
        setShowUpgradeModal(true);
      } else {
        // Show error message without alert popup
        setUpgradeMessage(error.message || 'Failed to create chatbot. Please try again.');
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
      // Update subscription status after deletion
      const updatedCount = chatbots.length - 1;
      setSubscriptionStatus({
        isPreviewMode: updatedCount >= 1,
        canAccessIntegrationScript: false,
        maxChatbots: 1,
        currentChatbotCount: updatedCount,
      });
    } catch (error: any) {
      console.error('Error deleting chatbot:', error);
      // Error is logged, user can see it in console if needed
      // No popup needed - deletion is already confirmed via confirm dialog
    }
  };

  const handleDeleteAllChatbots = async () => {
    if (!confirm(`Are you sure you want to delete ALL ${chatbots.length} chatbot(s)? This action cannot be undone and will reset your testing environment.`)) {
      return;
    }

    try {
      const result = await deleteAllChatbots();
      setChatbots([]);
      // Update subscription status after deletion
      setSubscriptionStatus({
        isPreviewMode: false,
        canAccessIntegrationScript: false,
        maxChatbots: 3, // Preview mode allows 3 chatbots
        currentChatbotCount: 0,
      });
      alert(`Successfully deleted ${result.deletedCount} chatbot(s).`);
    } catch (error: any) {
      console.error('Error deleting all chatbots:', error);
      alert('Failed to delete all chatbots. Please try again.');
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
    } catch (error: any) {
      console.error('Error logging out:', error);
      // Even if logout fails, try to redirect
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
    <main className="min-h-screen p-8">
      <ChatbotCreationLoader isVisible={creating} chatbotName="Your Chatbot" />
      <div className="max-w-7xl mx-auto">
        <div className="flex justify-between items-center mb-8">
          <div className="flex items-center gap-3">
            <Book className="w-10 h-10 text-brown-700" strokeWidth={1.5} />
            <div>
              <h1 className="text-4xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-brown-700 via-brown-600 to-gold-700">
                Prayer-Chat Dashboard
              </h1>
              {subscriptionStatus?.isPreviewMode && (
                <div className="flex items-center gap-2 mt-1">
                  <Sparkles className="w-4 h-4 text-gold-600" />
                  <span className="text-sm text-brown-600 font-medium">Preview Mode</span>
                </div>
              )}
            </div>
          </div>
          <div className="flex items-center gap-3">
            {chatbots.length > 0 && subscriptionStatus?.isPreviewMode && (
              <button
                onClick={handleDeleteAllChatbots}
                className="px-4 py-3 bg-red-600 text-white rounded-xl font-medium hover:bg-red-700 hover:shadow-lg transition-all flex items-center gap-2"
                title="Delete all chatbots (preview mode only - for testing)"
              >
                <Trash2 className="w-5 h-5" /> Delete All
              </button>
            )}
            <button
              onClick={() => setShowCreateForm(!showCreateForm)}
              className="px-6 py-3 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-xl font-medium hover:shadow-lg transition-all flex items-center gap-2"
            >
              {showCreateForm ? (
                <><X className="w-5 h-5" /> Cancel</>
              ) : (
                <><Plus className="w-5 h-5" /> Create New Chatbot</>
              )}
            </button>
            <button
              onClick={handleLogout}
              className="px-4 py-3 bg-brown-500 text-white rounded-xl font-medium hover:bg-brown-600 hover:shadow-lg transition-all flex items-center gap-2"
              title="Logout (volledig uitloggen)"
            >
              <LogOut className="w-5 h-5" /> Logout
            </button>
          </div>
        </div>

        {showCreateForm && (
          <motion.div
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            className="bg-brown-50/90 backdrop-blur-sm rounded-2xl shadow-xl p-8 mb-8 border border-brown-200"
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

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {chatbots.map((chatbot) => (
            <motion.div
              key={chatbot.id}
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              className="bg-brown-50/90 backdrop-blur-sm rounded-2xl shadow-lg p-6 hover:shadow-xl transition-all border border-brown-200"
            >
              <div className="flex items-center justify-between mb-3">
                <div className="flex items-center gap-2">
                  <Book className="w-5 h-5 text-brown-700" />
                  <h3 className="text-xl font-bold text-brown-800">{chatbot.name}</h3>
                </div>
                <button
                  onClick={() => handleDeleteChatbot(chatbot.id, chatbot.name)}
                  className="p-2 text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                  title="Delete chatbot"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
              <p className="text-brown-700 mb-4">{chatbot.description}</p>

              <div className="space-y-2">
                <Link
                  href={`/chatbot/${chatbot.id}`}
                  className="flex items-center justify-center gap-2 w-full px-4 py-2 bg-brown-100 text-brown-800 rounded-lg hover:bg-brown-200 transition-colors font-medium"
                >
                  <Eye className="w-4 h-4" />
                  Preview Chatbot
                </Link>

                <button
                  onClick={() => {
                    handleGetEmbedCode(chatbot.id);
                    setSelectedChatbot(chatbot);
                  }}
                  className={`flex items-center justify-center gap-2 w-full px-4 py-2 rounded-lg transition-colors font-medium ${
                    subscriptionStatus?.isPreviewMode
                      ? 'bg-brown-100 text-brown-600 hover:bg-brown-200 opacity-75'
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
                  onClick={() => setShowAnalysisForChatbot(
                    showAnalysisForChatbot === chatbot.id ? null : chatbot.id
                  )}
                  className="flex items-center justify-center gap-2 w-full px-4 py-2 rounded-lg transition-colors font-medium bg-brown-100 text-brown-600 hover:bg-brown-200"
                >
                  <Book className="w-4 h-4" />
                  {showAnalysisForChatbot === chatbot.id ? 'Hide' : 'Show'} Christian Content Analysis
                </button>
              </div>
              {showAnalysisForChatbot === chatbot.id && (
                <motion.div
                  initial={{ opacity: 0, height: 0 }}
                  animate={{ opacity: 1, height: 'auto' }}
                  exit={{ opacity: 0, height: 0 }}
                  className="mt-4 border-t border-brown-200 pt-4"
                >
                  <ChristianContentAnalysisComponent
                    chatbotId={chatbot.id}
                    chatbotName={chatbot.name}
                  />
                </motion.div>
              )}
            </motion.div>
          ))}
        </div>

        {chatbots.length === 0 && !showCreateForm && (
          <div className="text-center py-16">
            <Book className="w-20 h-20 text-brown-400 mx-auto mb-4" strokeWidth={1.5} />
            <p className="text-xl text-brown-700 mb-4">No chatbots yet</p>
            <button
              onClick={() => setShowCreateForm(true)}
              className="px-6 py-3 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-xl font-medium hover:shadow-lg transition-all inline-flex items-center gap-2"
            >
              <Plus className="w-5 h-5" />
              Create Your First Chatbot
            </button>
          </div>
        )}

        {embedCode && selectedChatbot && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50"
            onClick={() => setEmbedCode('')}
          >
            <div
              className="bg-brown-50 rounded-2xl p-8 max-w-2xl w-full border-2 border-brown-300 shadow-2xl"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-center gap-2 mb-4">
                <Code className="w-6 h-6 text-brown-700" />
                <h3 className="text-2xl font-bold text-brown-800">Embed Code for {selectedChatbot.name}</h3>
              </div>
              <pre className="bg-brown-100 p-4 rounded-lg overflow-x-auto mb-4 border border-brown-300 text-brown-900">
                <code>{embedCode}</code>
              </pre>
              <div className="flex gap-4">
                <button
                  onClick={async () => {
                    await navigator.clipboard.writeText(embedCode);
                    // Copy successful - no popup needed, user can see the code
                  }}
                  className="flex-1 px-4 py-2 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-lg hover:shadow-lg transition-all flex items-center justify-center gap-2"
                >
                  <Copy className="w-4 h-4" />
                  Copy Code
                </button>
                <button
                  onClick={() => setEmbedCode('')}
                  className="px-4 py-2 bg-brown-200 text-brown-800 rounded-lg hover:bg-brown-300 transition-colors flex items-center gap-2"
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
