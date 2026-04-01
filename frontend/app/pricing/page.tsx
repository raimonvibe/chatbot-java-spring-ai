'use client';

import { motion } from 'framer-motion';
import { Book, Check, Zap, Building2, Loader2 } from 'lucide-react';
import Link from 'next/link';
import { useSearchParams } from 'next/navigation';
import { Suspense, useEffect, useState } from 'react';
import { createCheckoutSession } from '@/lib/api';
import { isBillingEnabledFromEnv } from '@/lib/billing-config';

function getApiBaseUrl(): string {
  if (process.env.NEXT_PUBLIC_API_URL) return process.env.NEXT_PUBLIC_API_URL;
  if (typeof window !== 'undefined') {
    const hostname = window.location.hostname;
    if (hostname === 'prayer-chat.com' || hostname === 'www.prayer-chat.com') return 'https://chatbot-java-spring-ai.onrender.com';
    if (hostname.includes('vercel.app')) return 'https://chatbot-java-spring-ai.onrender.com';
  }
  return 'http://localhost:8081';
}
const API_BASE_URL = getApiBaseUrl();

// Plan keys sent to backend (must match backend: BASIC, PRO, ENTERPRISE)
type PlanKey = 'BASIC' | 'PRO' | 'ENTERPRISE';

// One chatbot per user; plan tier = website size (max pages). Aligned with backend PlanLimits.
const PLANS = [
  {
    id: 'FREE',
    name: 'Free Trial',
    price: 0,
    priceLabel: '$0',
    period: '/month',
    chatbots: 1,
    maxPagesPerScan: 500,
    messagesPerDay: 10,
    scansPerMonth: 1,
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
    chatbots: 1,
    maxPagesPerScan: 500,
    messagesPerDay: 100,
    scansPerMonth: 5,
    features: ['Christian messaging', 'Embed code'],
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
    chatbots: 1,
    maxPagesPerScan: 2000,
    messagesPerDay: 500,
    scansPerMonth: 20,
    features: ['Everything in Basic', 'Webhooks', 'Priority support'],
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
    chatbots: 1,
    maxPagesPerScan: 10000,
    messagesPerDay: 2000,
    scansPerMonth: 100,
    features: ['Everything in Pro', 'Dedicated support'],
    cta: 'Subscribe',
    planKey: 'ENTERPRISE' as PlanKey,
    buttonType: 'subscribe' as const,
    highlight: false,
  },
];

/** Plan limits from backend (GET /api/plans/limits). When set, overrides static maxPagesPerScan etc. */
interface PlanLimitsResponse {
  billingEnabled?: boolean;
  plans?: Record<string, { maxPagesPerScan: number; messagesPerDay: number; monthlyScanQuota: number }>;
  standardPageTiers?: Record<string, number>;
}

function PricingContent() {
  const searchParams = useSearchParams();
  const newUser = searchParams.get('new_user');
  const [limitsFromApi, setLimitsFromApi] = useState<PlanLimitsResponse | null>(null);
  const [subscribingPlan, setSubscribingPlan] = useState<PlanKey | null>(null);

  useEffect(() => {
    fetch(`${API_BASE_URL}/api/plans/limits`)
      .then((res) => (res.ok ? res.json() : null))
      .then((data: PlanLimitsResponse | null) => data && setLimitsFromApi(data))
      .catch(() => {});
  }, []);

  const billingOn = limitsFromApi?.billingEnabled ?? isBillingEnabledFromEnv();

  const plansToShow = PLANS.map((plan) => {
    const fromApiPlan = limitsFromApi?.plans?.[plan.id];
    const maxPages = fromApiPlan?.maxPagesPerScan ?? limitsFromApi?.standardPageTiers?.[plan.id] ?? plan.maxPagesPerScan;
    const messagesPerDay = fromApiPlan?.messagesPerDay ?? plan.messagesPerDay;
    const scansPerMonth = fromApiPlan?.monthlyScanQuota ?? plan.scansPerMonth;
    return { ...plan, maxPagesPerScan: maxPages, messagesPerDay, scansPerMonth };
  });

  const handleSubscribe = async (planKey: PlanKey) => {
    if (subscribingPlan) return;
    setSubscribingPlan(planKey);
    try {
      const url = await createCheckoutSession(planKey);
      window.location.href = url;
    } catch (error: unknown) {
      console.error('Error creating checkout session:', error);
      setSubscribingPlan(null);
      const message = error instanceof Error ? error.message : 'Failed to start subscription process. Please try again.';
      if (message === 'Unauthorized') {
        window.location.href = `/login?redirect=${encodeURIComponent('/pricing')}`;
        return;
      }
      if (message.toLowerCase().includes('not configured')) {
        alert('Payments are not configured yet. Please contact support or try again later.');
      } else {
        alert(message);
      }
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
                {billingOn
                  ? 'Welcome! To start creating chatbots, please choose a subscription plan below.'
                  : 'Welcome! Sign in to create your chatbot — this deployment is free within the limits below.'}
              </p>
            </div>
          )}

          <h2 className="text-3xl md:text-4xl font-bold text-brown-800 mb-4">
            {billingOn ? 'Choose Your Plan' : 'Plans & limits'}
          </h2>
          <p className="text-lg text-brown-700">
            {billingOn
              ? 'One chatbot per subscription. Choose the plan that fits your website size (max pages we analyze).'
              : 'Reference limits for this deployment. One chatbot per account; website scans are capped per plan tier.'}
          </p>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.6, delay: 0.2 }}
          className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-6 max-w-6xl w-full"
        >
          {plansToShow.map((plan) => (
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
                  <span className="text-brown-700 font-medium">1 chatbot · Website up to {plan.maxPagesPerScan.toLocaleString()} pages</span>
                </li>
                <li className="flex items-start gap-2">
                  <Check className="w-4 h-4 text-green-600 flex-shrink-0 mt-0.5" />
                  <span className="text-brown-700">{plan.messagesPerDay.toLocaleString()} messages/day</span>
                </li>
                <li className="flex items-start gap-2">
                  <Check className="w-4 h-4 text-green-600 flex-shrink-0 mt-0.5" />
                  <span className="text-brown-700">{plan.scansPerMonth} scan{plan.scansPerMonth !== 1 ? 's' : ''}/month</span>
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
              ) : billingOn ? (
                <button
                  type="button"
                  disabled={!!subscribingPlan}
                  onClick={() => plan.planKey && handleSubscribe(plan.planKey)}
                  className={`w-full px-4 py-2.5 rounded-lg font-semibold text-sm transition-all inline-flex items-center justify-center gap-2 disabled:opacity-70 disabled:cursor-not-allowed ${
                    plan.highlight
                      ? 'bg-gradient-to-r from-gold-500 to-gold-600 text-white hover:shadow-lg hover:scale-[1.02]'
                      : 'bg-brown-300 text-brown-800 hover:bg-brown-400'
                  }`}
                >
                  {subscribingPlan === plan.planKey ? (
                    <>
                      <Loader2 className="w-4 h-4 animate-spin" aria-hidden />
                      Redirecting…
                    </>
                  ) : (
                    plan.cta
                  )}
                </button>
              ) : (
                <Link
                  href="/login?redirect=/dashboard"
                  className="block w-full text-center px-4 py-2.5 bg-brown-200 text-brown-800 rounded-lg font-semibold hover:bg-brown-300 transition-all text-sm"
                >
                  Get started free
                </Link>
              )}
            </div>
          ))}
        </motion.div>

        {billingOn && (
          <motion.p
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.6, delay: 0.4 }}
            className="mt-6 text-brown-600 text-center text-sm"
          >
            All plans include secure payment processing via Stripe
          </motion.p>
        )}
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
