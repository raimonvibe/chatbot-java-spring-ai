'use client';

import { motion } from 'framer-motion';
import { Book, Check, Zap } from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense } from 'react';

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:8081';

function PricingContent() {
  const searchParams = useSearchParams();
  const newUser = searchParams.get('new_user');

  const handleSubscribe = async () => {
    try {
      // Call backend to create Stripe checkout session
      const response = await fetch(`${API_BASE_URL}/api/subscription/create-checkout-session`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
        },
      });

      if (!response.ok) {
        throw new Error('Failed to create checkout session');
      }

      const { url } = await response.json();
      // Redirect to Stripe checkout
      window.location.href = url;
    } catch (error) {
      console.error('Error creating checkout session:', error);
      alert('Failed to start subscription process. Please try again.');
    }
  };

  return (
    <main className="relative min-h-screen overflow-hidden">
      <div className="relative z-10 flex flex-col items-center justify-center min-h-screen p-4">
        <motion.div
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
          className="text-center max-w-4xl mb-12"
        >
          <div className="flex items-center justify-center gap-3 mb-6">
            <Book className="w-12 h-12 text-brown-700" strokeWidth={1.5} />
            <h1 className="text-5xl md:text-6xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-brown-700 via-brown-600 to-gold-700">
              Prayer-Chat
            </h1>
          </div>

          {newUser && (
            <div className="mb-6 p-4 bg-blue-50 border border-blue-200 rounded-lg">
              <p className="text-blue-700">
                Welcome! To start creating chatbots, please choose a subscription plan below.
              </p>
            </div>
          )}

          <h2 className="text-3xl md:text-4xl font-bold text-brown-800 mb-4">
            Choose Your Plan
          </h2>
          <p className="text-lg text-brown-700">
            Start building AI-powered chatbots with Christian values
          </p>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.6, delay: 0.2 }}
          className="grid grid-cols-1 md:grid-cols-2 gap-8 max-w-5xl w-full"
        >
          {/* Free Plan */}
          <div className="bg-brown-50/80 backdrop-blur-sm rounded-2xl shadow-lg p-8 border border-brown-200">
            <h3 className="text-2xl font-bold text-brown-800 mb-2">Free Trial</h3>
            <div className="mb-6">
              <span className="text-4xl font-bold text-brown-900">$0</span>
              <span className="text-brown-600">/month</span>
            </div>
            <ul className="space-y-3 mb-8">
              <li className="flex items-start gap-2">
                <Check className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
                <span className="text-brown-700">1 Chatbot</span>
              </li>
              <li className="flex items-start gap-2">
                <Check className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
                <span className="text-brown-700">100 Messages/month</span>
              </li>
              <li className="flex items-start gap-2">
                <Check className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
                <span className="text-brown-700">Basic website analysis</span>
              </li>
              <li className="flex items-start gap-2">
                <Check className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
                <span className="text-brown-700">Christian messaging</span>
              </li>
            </ul>
            <Link
              href="/dashboard"
              className="block w-full text-center px-6 py-3 bg-brown-200 text-brown-800 rounded-lg font-semibold hover:bg-brown-300 transition-all"
            >
              Get Started Free
            </Link>
          </div>

          {/* Pro Plan */}
          <div className="bg-gradient-to-br from-gold-100 to-brown-100 backdrop-blur-sm rounded-2xl shadow-2xl p-8 border-2 border-gold-400 relative">
            <div className="absolute -top-4 left-1/2 -translate-x-1/2 bg-gradient-to-r from-gold-500 to-gold-600 text-white px-4 py-1 rounded-full text-sm font-semibold flex items-center gap-1">
              <Zap className="w-4 h-4" />
              Most Popular
            </div>
            <h3 className="text-2xl font-bold text-brown-900 mb-2">Pro Plan</h3>
            <div className="mb-6">
              <span className="text-4xl font-bold text-brown-900">$29</span>
              <span className="text-brown-700">/month</span>
            </div>
            <ul className="space-y-3 mb-8">
              <li className="flex items-start gap-2">
                <Check className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
                <span className="text-brown-800 font-medium">Unlimited Chatbots</span>
              </li>
              <li className="flex items-start gap-2">
                <Check className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
                <span className="text-brown-800 font-medium">Unlimited Messages</span>
              </li>
              <li className="flex items-start gap-2">
                <Check className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
                <span className="text-brown-800 font-medium">Advanced website analysis</span>
              </li>
              <li className="flex items-start gap-2">
                <Check className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
                <span className="text-brown-800 font-medium">Custom Bible verses</span>
              </li>
              <li className="flex items-start gap-2">
                <Check className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
                <span className="text-brown-800 font-medium">Priority support</span>
              </li>
              <li className="flex items-start gap-2">
                <Check className="w-5 h-5 text-green-600 flex-shrink-0 mt-0.5" />
                <span className="text-brown-800 font-medium">Webhook integrations</span>
              </li>
            </ul>
            <button
              onClick={handleSubscribe}
              className="w-full px-6 py-3 bg-gradient-to-r from-gold-500 to-gold-600 text-white rounded-lg font-semibold hover:shadow-xl hover:scale-105 transition-all"
            >
              Subscribe Now
            </button>
          </div>
        </motion.div>

        <motion.p
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.6, delay: 0.4 }}
          className="mt-8 text-brown-600 text-center"
        >
          All plans include secure payment processing via Stripe
        </motion.p>
      </div>
    </main>
  );
}

export default function PricingPage() {
  return (
    <Suspense fallback={<div>Loading...</div>}>
      <PricingContent />
    </Suspense>
  );
}
