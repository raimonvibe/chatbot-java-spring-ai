'use client';

import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { useRouter } from 'next/navigation';
import { Book, Sparkles } from 'lucide-react';
import {
  createChatbotFromUrl,
  getAllChatbots,
  checkAuth,
  getSubscriptionStatusFromApi,
  isApiError,
} from '@/lib/api';
import { isBillingEnabledFromEnv, paymentActionsAvailableFromApi } from '@/lib/billing-config';
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
  const [billingActionsAvailable, setBillingActionsAvailable] = useState(() => isBillingEnabledFromEnv());

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
        try {
          const sub = await getSubscriptionStatusFromApi();
          setBillingActionsAvailable(paymentActionsAvailableFromApi(sub));
        } catch {
          setBillingActionsAvailable(isBillingEnabledFromEnv());
        }
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
      const tooLarge =
        isApiError(err) &&
        (err.status === 402 || err.upgradeRequired === true || err.websiteTooLarge === true);

      if (tooLarge) {
        const m =
          (err instanceof Error && err.message) ||
          'This site has more pages than we can scan at once (up to 500 per scan). Try a smaller section or subdomain.';
        setUpgradeMessage(m);
        setPaywallFeature('general');
        if (billingActionsAvailable) {
          setShowUpgradeModal(true);
        } else {
          setError(m);
        }
        setCreating(false);
        return;
      }

      const msg = err instanceof Error && typeof err.message === 'string' ? err.message : '';
      if (msg && (msg.includes('limit') || msg.includes('Upgrade'))) {
        setUpgradeMessage(msg || 'One chatbot per account limit reached. Upgrade to create more.');
        setPaywallFeature('chatbot-limit');
        if (billingActionsAvailable) {
          setShowUpgradeModal(true);
        } else {
          setError(msg || 'You have reached the limit for your account.');
        }
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
    <main className="min-h-screen flex flex-col items-center justify-center p-3 sm:p-4 pb-8 sm:pb-4 bg-gradient-to-br from-brown-50 via-gold-50/30 to-brown-50">
      <ChatbotCreationLoader isVisible={creating} chatbotName="Your Chatbot" />
      <PaywallModal
        isOpen={showUpgradeModal}
        onClose={() => setShowUpgradeModal(false)}
        title="Upgrade to Scan Larger Websites"
        message={upgradeMessage}
        feature={paywallFeature}
        billingActionsAvailable={billingActionsAvailable}
      />
      
      <motion.div
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        className="bg-brown-50/90 backdrop-blur-sm rounded-2xl shadow-xl p-4 sm:p-6 md:p-8 max-w-lg w-full max-h-[calc(100dvh-2rem)] overflow-y-auto border border-brown-200"
      >
        <div className="text-center mb-6 sm:mb-8">
          <div className="flex items-center justify-center gap-2 sm:gap-3 mb-3 sm:mb-4">
            <Book className="w-9 h-9 sm:w-10 sm:h-10 text-brown-700 shrink-0" strokeWidth={1.5} />
            <Sparkles className="w-7 h-7 sm:w-8 sm:h-8 text-gold-600 shrink-0" strokeWidth={1.5} />
          </div>
          <h1 className="text-2xl sm:text-3xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-brown-700 via-brown-600 to-gold-700 mb-2 text-pretty px-1">
            Welcome to Prayer-Chat
          </h1>
          <p className="text-brown-700 text-base sm:text-lg text-pretty px-1">
            Let&apos;s create your first AI chatbot in seconds
          </p>
        </div>

        <CreateChatbotFromWebsiteForm
          variant="onboarding"
          onSubmit={handleCreateFromUrl}
          submitting={creating}
          serverError={error}
          onClearServerError={() => setError('')}
        />
      </motion.div>
    </main>
  );
}

