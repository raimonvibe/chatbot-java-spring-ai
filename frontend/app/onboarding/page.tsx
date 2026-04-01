'use client';

import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { useRouter } from 'next/navigation';
import { Book, ChevronDown, Sparkles } from 'lucide-react';
import { createChatbotFromUrl, getAllChatbots, checkAuth, getSubscriptionStatusFromApi } from '@/lib/api';
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
      const anyErr = err as { status?: number; upgradeRequired?: boolean; message?: string };

      if (anyErr.status === 402 || anyErr.upgradeRequired) {
        const m =
          anyErr.message ||
          'This website is larger than we can scan in one run. Try your homepage or a smaller section of the site.';
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

      const msg = typeof anyErr.message === 'string' ? anyErr.message : '';
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

        <details className="group mt-5 sm:mt-6 rounded-xl border border-brown-200/90 bg-brown-50/50 text-left open:bg-brown-50/70 [touch-action:manipulation]">
          <summary className="flex min-h-12 sm:min-h-0 cursor-pointer list-none items-center justify-between gap-3 px-3 sm:px-3.5 py-3 sm:py-2.5 text-xs sm:text-sm font-medium text-brown-700 select-none [&::-webkit-details-marker]:hidden">
            <span className="flex min-w-0 flex-1 items-center gap-1.5">
              <Sparkles className="h-4 w-4 sm:h-3.5 sm:w-3.5 shrink-0 text-gold-600" aria-hidden />
              <span className="text-pretty leading-snug">Plan &amp; preview limits</span>
            </span>
            <ChevronDown className="h-5 w-5 sm:h-4 sm:w-4 shrink-0 text-brown-500 transition-transform duration-200 group-open:rotate-180" aria-hidden />
          </summary>
          <p className="border-t border-brown-200/80 px-3 sm:px-3.5 py-3 sm:py-2.5 text-xs sm:text-sm leading-relaxed text-brown-600 text-pretty">
            In preview mode you can create a limited number of chatbots for testing. Upgrade when you are ready for production
            traffic and larger site scans.
          </p>
        </details>
      </motion.div>
    </main>
  );
}

