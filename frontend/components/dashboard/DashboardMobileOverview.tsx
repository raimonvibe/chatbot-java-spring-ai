'use client';

import Link from 'next/link';
import { Plus, X, CreditCard, User } from 'lucide-react';
import type { Chatbot, SubscriptionStatus } from '@/lib/api';
import { isBillingEnabledFromEnv, paymentActionsAvailableFromApi } from '@/lib/billing-config';

interface DashboardMobileOverviewProps {
  subscriptionStatus: SubscriptionStatus | null;
  chatbots: Chatbot[];
  showCreateForm: boolean;
  portalLoading: boolean;
  onToggleCreateForm: () => void;
  onOpenSubscription: () => void;
}

export default function DashboardMobileOverview({
  subscriptionStatus,
  chatbots,
  showCreateForm,
  portalLoading,
  onToggleCreateForm,
  onOpenSubscription,
}: DashboardMobileOverviewProps) {
  const offerPaymentUi = subscriptionStatus
    ? paymentActionsAvailableFromApi(subscriptionStatus)
    : isBillingEnabledFromEnv();
  const canAddChatbot = subscriptionStatus ? chatbots.length < subscriptionStatus.maxChatbots : true;

  return (
    <section className="sm:hidden mb-6">
      <div className="rounded-2xl border border-brown-100 bg-brown-50/80 shadow-sm p-4">
        <div className="flex items-start justify-between gap-3">
          <div className="min-w-0">
            <p className="text-xs uppercase tracking-wide text-brown-600 font-semibold">Overview</p>
            <p className="text-brown-900 font-bold text-lg leading-tight">Your Dashboard</p>
            <p className="text-brown-700 text-sm mt-1">
              {subscriptionStatus?.isPreviewMode
                ? offerPaymentUi
                  ? 'Preview mode: embed is locked until subscription is active.'
                  : 'Manage chatbots and copy your embed code.'
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
          {canAddChatbot ? (
            <button
              type="button"
              onClick={onToggleCreateForm}
              className="w-full px-4 py-3 rounded-2xl bg-gradient-to-r from-brown-600 to-gold-600 text-white font-semibold flex items-center justify-center gap-2"
            >
              {showCreateForm ? <X className="w-5 h-5" /> : <Plus className="w-5 h-5" />}
              {showCreateForm ? 'Cancel' : 'New chatbot'}
            </button>
          ) : null}
          {offerPaymentUi ? (
            <button
              type="button"
              onClick={onOpenSubscription}
              disabled={portalLoading}
              className="w-full px-4 py-3 rounded-2xl bg-white border border-brown-200 text-brown-900 font-semibold flex items-center justify-center gap-2 disabled:opacity-60"
            >
              <CreditCard className="w-5 h-5" />
              {portalLoading ? 'Opening…' : 'Subscription'}
            </button>
          ) : null}
        </div>
      </div>
    </section>
  );
}
