'use client';

import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { useRouter } from 'next/navigation';
import { Book, Sparkles, ArrowRight, Loader2 } from 'lucide-react';
import { createChatbotFromUrl, getAllChatbots, checkAuth } from '@/lib/api';
import ChatbotCreationLoader from '@/components/ChatbotCreationLoader';
import PaywallModal from '@/components/PaywallModal';

export default function OnboardingPage() {
  const router = useRouter();
  const [websiteUrl, setWebsiteUrl] = useState('');
  const [loading, setLoading] = useState(true);
  const [authenticated, setAuthenticated] = useState(false);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState('');
  const [showUpgradeModal, setShowUpgradeModal] = useState(false);
  const [upgradeMessage, setUpgradeMessage] = useState('');
  const [paywallFeature, setPaywallFeature] = useState<'chatbot-limit' | 'integration-script' | 'advanced-features' | 'general'>('general');

  useEffect(() => {
    checkAuthAndChatbots();
  }, []);

  // Redirect to login if not authenticated (use useEffect to avoid showing modal)
  // This must be before early returns to maintain hook order
  useEffect(() => {
    if (!loading && !authenticated) {
      router.replace('/login');
    }
  }, [loading, authenticated, router]);

  const checkAuthAndChatbots = async () => {
    try {
      const authResult = await checkAuth();
      setAuthenticated(authResult.authenticated);
      
      if (authResult.authenticated) {
        // Check if user already has chatbots
        const chatbots = await getAllChatbots();
        if (chatbots.length > 0) {
          // User already has chatbots, redirect to dashboard
          router.push('/dashboard');
          return;
        }
      }
    } catch (error) {
      console.error('Error checking auth:', error);
      setAuthenticated(false);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setCreating(true);

    try {
      if (!websiteUrl.trim()) {
        setError('Please enter a website URL');
        setCreating(false);
        return;
      }

      // Create chatbot (analysis runs in background; preview page shows "Setting up..." until ready)
      await createChatbotFromUrl(websiteUrl.trim());
      router.push('/dashboard');
    } catch (err: any) {
      console.error('Error creating chatbot:', err);
      
      // Check if it's a payment required error (402) - website size limit
      if (err.status === 402 || err.upgradeRequired) {
        setUpgradeMessage(err.message || 'Website too large for preview mode. Upgrade to continue.');
        setPaywallFeature('general');
        setShowUpgradeModal(true);
        setCreating(false);
        return;
      }
      
      // Check if it's a limit reached error
      if (err.message && (err.message.includes('limit') || err.message.includes('Upgrade'))) {
        setUpgradeMessage(err.message || 'One chatbot per account limit reached. Upgrade to create more.');
        setPaywallFeature('chatbot-limit');
        setShowUpgradeModal(true);
        setCreating(false);
        return;
      }
      
      setError(err.message || 'Failed to create chatbot. Please try again.');
      setCreating(false);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center">
        <Book className="w-16 h-16 text-brown-600 animate-pulse mb-4" strokeWidth={1.5} />
        <div className="text-xl text-brown-700">Loading...</div>
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
    <main className="min-h-screen flex flex-col items-center justify-center p-4 bg-gradient-to-br from-brown-50 via-gold-50/30 to-brown-50">
      <ChatbotCreationLoader isVisible={creating} chatbotName="Your Chatbot" />
      <PaywallModal
        isOpen={showUpgradeModal}
        onClose={() => setShowUpgradeModal(false)}
        title="Upgrade to Scan Larger Websites"
        message={upgradeMessage}
        feature={paywallFeature}
      />
      
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="bg-brown-50/90 backdrop-blur-sm rounded-2xl shadow-xl p-8 max-w-lg w-full border border-brown-200"
      >
        <div className="text-center mb-8">
          <div className="flex items-center justify-center gap-3 mb-4">
            <Book className="w-10 h-10 text-brown-700" strokeWidth={1.5} />
            <Sparkles className="w-8 h-8 text-gold-600" strokeWidth={1.5} />
          </div>
          <h1 className="text-3xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-brown-700 via-brown-600 to-gold-700 mb-2">
            Welcome to Prayer-Chat
          </h1>
          <p className="text-brown-700 text-lg">
            Let's create your first AI chatbot in seconds
          </p>
        </div>

        <form onSubmit={handleSubmit} className="space-y-6">
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

          {error && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-3 text-red-700 text-sm">
              {error}
            </div>
          )}

          <button
            type="submit"
            disabled={creating || !websiteUrl.trim()}
            className="w-full px-6 py-3 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-lg font-semibold hover:shadow-xl transition-all disabled:opacity-50 disabled:cursor-not-allowed flex items-center justify-center gap-2"
          >
            {creating ? (
              <>
                <Loader2 className="w-5 h-5 animate-spin" />
                Creating your chatbot...
              </>
            ) : (
              <>
                Create My Chatbot
                <ArrowRight className="w-5 h-5" />
              </>
            )}
          </button>
        </form>

        <div className="mt-6 pt-6 border-t border-brown-200">
          <p className="text-xs text-brown-600 text-center">
            <Sparkles className="w-3 h-3 inline mr-1" />
            Preview Mode: You can create up to 3 chatbots for testing
          </p>
        </div>
      </motion.div>
    </main>
  );
}

