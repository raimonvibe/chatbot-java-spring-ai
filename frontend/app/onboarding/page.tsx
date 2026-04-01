'use client';

import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { useRouter } from 'next/navigation';
import { Book, Sparkles } from 'lucide-react';
import { createChatbotFromUrl, getAllChatbots, checkAuth } from '@/lib/api';
import ChatbotCreationLoader from '@/components/ChatbotCreationLoader';
import CreateChatbotFromWebsiteForm from '@/components/CreateChatbotFromWebsiteForm';
import PaywallModal from '@/components/PaywallModal';

export default function OnboardingPage() {
  const router = useRouter();
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

  const handleCreateFromUrl = async (canonicalUrl: string) => {
    setError('');
    setCreating(true);

    try {
      await createChatbotFromUrl(canonicalUrl);
      router.push('/dashboard');
    } catch (err: unknown) {
      console.error('Error creating chatbot:', err);
      const anyErr = err as { status?: number; upgradeRequired?: boolean; message?: string };

      if (anyErr.status === 402 || anyErr.upgradeRequired) {
        setUpgradeMessage(anyErr.message || 'Website too large for preview mode. Upgrade to continue.');
        setPaywallFeature('general');
        setShowUpgradeModal(true);
        setCreating(false);
        return;
      }

      const msg = typeof anyErr.message === 'string' ? anyErr.message : '';
      if (msg && (msg.includes('limit') || msg.includes('Upgrade'))) {
        setUpgradeMessage(msg || 'One chatbot per account limit reached. Upgrade to create more.');
        setPaywallFeature('chatbot-limit');
        setShowUpgradeModal(true);
        setCreating(false);
        return;
      }

      setError(msg || 'Failed to create chatbot. Please try again.');
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

        <CreateChatbotFromWebsiteForm
          variant="onboarding"
          onSubmit={handleCreateFromUrl}
          submitting={creating}
          serverError={error}
          onClearServerError={() => setError('')}
        />

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

