'use client';

import { motion, AnimatePresence } from 'framer-motion';
import { Crown, X, Sparkles, Book, Zap } from 'lucide-react';
import { useState } from 'react';

interface PaywallModalProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  message?: string;
  feature?: 'chatbot-limit' | 'integration-script' | 'advanced-features' | 'general';
  bibleVerse?: {
    text: string;
    reference: string;
  };
}

// Bible verses for different contexts
const defaultBibleVerses = {
  'chatbot-limit': {
    text: 'For I know the plans I have for you, declares the Lord, plans to prosper you and not to harm you, plans to give you hope and a future.',
    reference: 'Jeremiah 29:11'
  },
  'integration-script': {
    text: 'Let your light shine before others, that they may see your good deeds and glorify your Father in heaven.',
    reference: 'Matthew 5:16'
  },
  'advanced-features': {
    text: 'Commit to the Lord whatever you do, and he will establish your plans.',
    reference: 'Proverbs 16:3'
  },
  'general': {
    text: 'For I know the plans I have for you, declares the Lord, plans to prosper you and not to harm you, plans to give you hope and a future.',
    reference: 'Jeremiah 29:11'
  }
};

// Feature-specific messages
const featureMessages = {
  'chatbot-limit': {
    title: 'Unlock Unlimited Chatbots',
    message: 'You\'ve reached the limit for your current plan. Upgrade to create unlimited chatbots and share your message with more people!'
  },
  'integration-script': {
    title: 'Share Your Message Widely',
    message: 'Integration scripts are available for paid plans. Upgrade to embed your chatbot on any website and reach more people with your message!'
  },
  'advanced-features': {
    title: 'Unlock Advanced Features',
    message: 'This feature is available for paid plans. Upgrade to access advanced analytics, custom branding, and more!'
  },
  'general': {
    title: 'Upgrade to Share Your Message',
    message: 'We\'d love to help you share your message more widely! Upgrade to unlock more features and reach more people.'
  }
};

function getApiBaseUrl(): string {
  if (process.env.NEXT_PUBLIC_API_URL) {
    return process.env.NEXT_PUBLIC_API_URL;
  }
  
  if (typeof window !== 'undefined') {
    const hostname = window.location.hostname;
    if (hostname === 'prayer-chat.com' || hostname === 'www.prayer-chat.com') {
      return 'https://chatbot-backend-4mp4.onrender.com';
    }
    if (hostname.includes('vercel.app')) {
      return 'https://chatbot-backend-4mp4.onrender.com';
    }
  }
  
  return 'http://localhost:8081';
}

const API_BASE_URL = getApiBaseUrl();

export default function PaywallModal({
  isOpen,
  onClose,
  title,
  message,
  feature = 'general',
  bibleVerse
}: PaywallModalProps) {
  const [loading, setLoading] = useState(false);

  const featureConfig = featureMessages[feature];
  const displayTitle = title || featureConfig.title;
  const displayMessage = message || featureConfig.message;
  const verse = bibleVerse || defaultBibleVerses[feature];

  const handleUpgrade = async () => {
    setLoading(true);
    try {
      // Call backend to create Stripe checkout session
      const response = await fetch(`${API_BASE_URL}/api/subscription/create-checkout-session`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({}),
      });

      if (!response.ok) {
        const errorData = await response.json().catch(() => ({ error: 'Failed to create checkout session' }));
        throw new Error(errorData.error || 'Failed to create checkout session');
      }

      const { url } = await response.json();
      // Redirect to Stripe checkout
      window.location.href = url;
    } catch (error: any) {
      console.error('Error creating checkout session:', error);
      alert(error.message || 'Failed to start subscription process. Please try again.');
      setLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <AnimatePresence>
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        exit={{ opacity: 0 }}
        className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50"
        onClick={onClose}
      >
        <motion.div
          initial={{ opacity: 0, scale: 0.95, y: 20 }}
          animate={{ opacity: 1, scale: 1, y: 0 }}
          exit={{ opacity: 0, scale: 0.95, y: 20 }}
          transition={{ type: 'spring', duration: 0.3 }}
          className="bg-gradient-to-br from-brown-50 via-gold-50 to-brown-50 rounded-2xl p-8 max-w-lg w-full border-2 border-gold-300 shadow-2xl relative"
          onClick={(e) => e.stopPropagation()}
        >
          {/* Close button */}
          <button
            onClick={onClose}
            className="absolute top-4 right-4 p-2 text-brown-600 hover:text-brown-800 hover:bg-brown-100 rounded-full transition-colors"
            aria-label="Close"
          >
            <X className="w-5 h-5" />
          </button>

          <div className="text-center mb-6">
            {/* Icon */}
            <div className="w-20 h-20 bg-gradient-to-br from-gold-100 to-gold-200 rounded-full flex items-center justify-center mx-auto mb-4 shadow-lg">
              <Crown className="w-10 h-10 text-gold-700" />
            </div>

            {/* Title */}
            <h3 className="text-3xl font-bold text-brown-800 mb-3">
              {displayTitle}
            </h3>

            {/* Message */}
            <p className="text-brown-700 mb-6 text-lg leading-relaxed">
              {displayMessage}
            </p>

            {/* Bible Verse */}
            <div className="bg-gradient-to-r from-brown-100 to-gold-100 border-l-4 border-gold-600 p-5 rounded-lg mb-6 text-left shadow-sm">
              <div className="flex items-start gap-3">
                <Book className="w-5 h-5 text-gold-700 flex-shrink-0 mt-0.5" />
                <div>
                  <p className="text-sm text-brown-800 italic leading-relaxed mb-2">
                    &quot;{verse.text}&quot;
                  </p>
                  <p className="text-xs text-brown-600 font-semibold">
                    — {verse.reference}
                  </p>
                </div>
              </div>
            </div>

            {/* Features list */}
            <div className="grid grid-cols-1 gap-3 mb-6 text-left">
              <div className="flex items-center gap-3 bg-white/50 rounded-lg p-3">
                <Sparkles className="w-5 h-5 text-gold-600 flex-shrink-0" />
                <span className="text-brown-800 font-medium">Unlimited chatbots</span>
              </div>
              <div className="flex items-center gap-3 bg-white/50 rounded-lg p-3">
                <Zap className="w-5 h-5 text-gold-600 flex-shrink-0" />
                <span className="text-brown-800 font-medium">Integration scripts</span>
              </div>
              <div className="flex items-center gap-3 bg-white/50 rounded-lg p-3">
                <Crown className="w-5 h-5 text-gold-600 flex-shrink-0" />
                <span className="text-brown-800 font-medium">Advanced analytics</span>
              </div>
            </div>
          </div>

          {/* Action buttons */}
          <div className="flex gap-4">
            <button
              onClick={handleUpgrade}
              disabled={loading}
              className="flex-1 px-6 py-4 bg-gradient-to-r from-brown-600 via-gold-600 to-brown-600 text-white rounded-lg hover:shadow-xl hover:scale-105 transition-all flex items-center justify-center gap-2 font-semibold text-lg disabled:opacity-50 disabled:cursor-not-allowed disabled:hover:scale-100"
            >
              {loading ? (
                <>
                  <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  <span>Processing...</span>
                </>
              ) : (
                <>
                  <Crown className="w-6 h-6" />
                  <span>Upgrade Now</span>
                </>
              )}
            </button>
            <button
              onClick={onClose}
              className="px-6 py-4 bg-brown-200 text-brown-800 rounded-lg hover:bg-brown-300 transition-colors font-medium"
            >
              Maybe Later
            </button>
          </div>

          {/* Pricing hint */}
          <p className="text-center text-sm text-brown-600 mt-4">
            Starting at $29/month • Cancel anytime
          </p>
        </motion.div>
      </motion.div>
    </AnimatePresence>
  );
}

