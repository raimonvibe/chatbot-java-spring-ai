'use client';

import { motion } from 'framer-motion';
import { Book, Check, Zap, Building2 } from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense } from 'react';

// Auto-detect backend URL based on environment
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

// Plan keys sent to backend (must match backend: BASIC, PRO, ENTERPRISE)
type PlanKey = 'BASIC' | 'PRO' | 'ENTERPRISE';

// Limits aligned with backend PlanLimits
const PLANS = [
  {
    id: 'FREE',
    name: 'Free Trial',
    price: 0,
    priceLabel: '$0',
    period: '/month',
    chatbots: 1,
    messagesPerDay: 10,
    scansPerMonth: 1,
    maxPagesPerScan: 50,
    features: ['Christian messaging'],
    cta: 'Get Started Free',
    href: '/dashboard',
    buttonType: 'link' as const,
    highlight: false,
  },
  {
    id: 'BASIC',
    name: 'Basic',
    price: 12,
    priceLabel: '$12',
    period: '/month',
    chatbots: 3,
    messagesPerDay: 100,
    scansPerMonth: 5,
    maxPagesPerScan: 500,
    features: ['Christian messaging', 'Small sites'],
    cta: 'Subscribe',
    planKey: 'BASIC' as PlanKey,
    buttonType: 'subscribe' as const,
    highlight: false,
  },
  {
    id: 'PRO',
    name: 'Pro',
    price: 29,
    priceLabel: '$29',
    period: '/month',
    chatbots: 10,
    messagesPerDay: 500,
    scansPerMonth: 20,
    maxPagesPerScan: 2000,
    features: ['Everything in Basic', 'Custom Bible verses', 'Priority support', 'Webhook integrations'],
    cta: 'Subscribe',
    planKey: 'PRO' as PlanKey,
    buttonType: 'subscribe' as const,
    highlight: true,
  },
  {
    id: 'ENTERPRISE',
    name: 'Enterprise',
    price: 79,
    priceLabel: '$79',
    period: '/month',
    chatbots: 50,
    messagesPerDay: 2000,
    scansPerMonth: 100,
    maxPagesPerScan: 10000,
    features: ['Everything in Pro', 'Large sites & docs', 'Dedicated support'],
    cta: 'Subscribe',
    planKey: 'ENTERPRISE' as PlanKey,
    buttonType: 'subscribe' as const,
    highlight: false,
  },
];

function PricingContent() {
  const searchParams = useSearchParams();
  const newUser = searchParams.get('new_user');

  const handleSubscribe = async (planKey: PlanKey) => {
    try {
      const response = await fetch(`${API_BASE_URL}/api/subscription/create-checkout-session`, {
        method: 'POST',
        credentials: 'include',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ plan: planKey }),
      });

      if (!response.ok) {
        if (response.status === 401) {
          window.location.href = `/login?redirect=${encodeURIComponent('/pricing')}`;
          return;
        }
        const contentType = response.headers.get('content-type');
        const data = contentType?.includes('application/json')
          ? await response.json().catch(() => ({}))
          : {};
        const message = data?.error || 'Failed to create checkout session';
        if (response.status === 503 && message.toLowerCase().includes('not configured')) {
          alert('Payments are not configured yet. Please contact support or try again later.');
        } else {
          alert(message);
        }
        return;
      }

      const data = await response.json();
      const url = data.checkoutUrl || data.url;
      if (!url || typeof url !== 'string') {
        alert('Invalid response from server. Please try again.');
        return;
      }
      try {
        const urlObj = new URL(url);
        if (!['checkout.stripe.com', 'checkout.stripe.dev'].includes(urlObj.hostname)) {
          alert('Invalid checkout URL. Please try again.');
          return;
        }
      } catch {
        alert('Invalid checkout URL. Please try again.');
        return;
      }
      window.location.href = url;
    } catch (error) {
      console.error('Error creating checkout session:', error);
      alert('Failed to start subscription process. Please try again.');
    }
  };

  return (
    <main className="relative min-h-screen overflow-hidden">
      <div className="relative z-10 flex flex-col items-center justify-center min-h-screen p-4 pb-12">
        <motion.div
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6 }}
          className="text-center max-w-4xl mb-8"
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
            Plans scale with website size: more pages per scan and more scans per month as you grow.
          </p>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.6, delay: 0.2 }}
          className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6 max-w-6xl w-full"
        >
          {PLANS.map((plan) => (
            <div
              key={plan.id}
              className={`rounded-2xl shadow-lg p-6 border-2 flex flex-col ${
                plan.highlight
                  ? 'bg-gradient-to-br from-gold-100 to-brown-100 border-gold-400 relative'
                  : 'bg-brown-50/80 border-brown-200'
              }`}
            >
              {plan.highlight && (
                <div className="absolute -top-3 left-1/2 -translate-x-1/2 bg-gradient-to-r from-gold-500 to-gold-600 text-white px-3 py-1 rounded-full text-xs font-semibold flex items-center gap-1">
                  <Zap className="w-3 h-3" />
                  Most Popular
                </div>
              )}
              <div className="flex items-center gap-2 mb-2">
                {plan.id === 'ENTERPRISE' && <Building2 className="w-5 h-5 text-brown-600" />}
                <h3 className="text-xl font-bold text-brown-800">{plan.name}</h3>
              </div>
              <div className="mb-4">
                <span className="text-3xl font-bold text-brown-900">{plan.priceLabel}</span>
                <span className="text-brown-600">{plan.period}</span>
              </div>
              <ul className="space-y-2 mb-6 flex-1 text-sm">
                <li className="flex items-start gap-2">
                  <Check className="w-4 h-4 text-green-600 flex-shrink-0 mt-0.5" />
                  <span className="text-brown-700">{plan.chatbots} Chatbot{plan.chatbots !== 1 ? 's' : ''}</span>
                </li>
                <li className="flex items-start gap-2">
                  <Check className="w-4 h-4 text-green-600 flex-shrink-0 mt-0.5" />
                  <span className="text-brown-700">{plan.messagesPerDay.toLocaleString()} messages/day</span>
                </li>
                <li className="flex items-start gap-2">
                  <Check className="w-4 h-4 text-green-600 flex-shrink-0 mt-0.5" />
                  <span className="text-brown-700">{plan.scansPerMonth} scan{plan.scansPerMonth !== 1 ? 's' : ''}/month (up to {plan.maxPagesPerScan.toLocaleString()} pages/scan)</span>
                </li>
                {plan.features.map((f) => (
                  <li key={f} className="flex items-start gap-2">
                    <Check className="w-4 h-4 text-green-600 flex-shrink-0 mt-0.5" />
                    <span className="text-brown-700">{f}</span>
                  </li>
                ))}
              </ul>
              {plan.buttonType === 'link' && plan.href ? (
                <Link
                  href={plan.href}
                  className="block w-full text-center px-4 py-2.5 bg-brown-200 text-brown-800 rounded-lg font-semibold hover:bg-brown-300 transition-all text-sm"
                >
                  {plan.cta}
                </Link>
              ) : (
                <button
                  type="button"
                  onClick={() => plan.planKey && handleSubscribe(plan.planKey)}
                  className={`w-full px-4 py-2.5 rounded-lg font-semibold text-sm transition-all ${
                    plan.highlight
                      ? 'bg-gradient-to-r from-gold-500 to-gold-600 text-white hover:shadow-lg hover:scale-[1.02]'
                      : 'bg-brown-300 text-brown-800 hover:bg-brown-400'
                  }`}
                >
                  {plan.cta}
                </button>
              )}
            </div>
          ))}
        </motion.div>

        <motion.p
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.6, delay: 0.4 }}
          className="mt-6 text-brown-600 text-center text-sm"
        >
          All plans include secure payment processing via Stripe
        </motion.p>
      </div>
    </main>
  );
}

export default function PricingPage() {
  return (
    <Suspense fallback={<div className="min-h-screen flex items-center justify-center">Loading...</div>}>
      <PricingContent />
    </Suspense>
  );
}
