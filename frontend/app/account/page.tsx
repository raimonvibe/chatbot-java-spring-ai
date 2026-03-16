'use client';

import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import {
  User,
  Mail,
  CreditCard,
  Shield,
  LogOut,
  ExternalLink,
  FileText,
  MessageCircle,
  Loader2,
  Code,
  Copy,
} from 'lucide-react';
import {
  checkAuth,
  logout,
  createPortalSession,
  getSubscriptionStatusFromApi,
  getAllChatbots,
  getEmbedCode,
  type SubscriptionStatusApi,
  type Chatbot,
} from '@/lib/api';

export default function AccountPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [user, setUser] = useState<{ id: number; username: string; email?: string; authProvider?: string; picture?: string } | null>(null);
  const [subscription, setSubscription] = useState<SubscriptionStatusApi | null | 'error'>(null);
  const [portalLoading, setPortalLoading] = useState(false);
  const [logoutLoading, setLogoutLoading] = useState(false);
  const [chatbots, setChatbots] = useState<Chatbot[]>([]);
  const [embedCode, setEmbedCode] = useState<string | null>(null);
  const [embedLoading, setEmbedLoading] = useState(false);

  useEffect(() => {
    const load = async () => {
      const auth = await checkAuth();
      if (!auth.authenticated || !auth.user) {
        router.replace('/login');
        return;
      }
      setUser(auth.user);
      try {
        const sub = await getSubscriptionStatusFromApi();
        setSubscription(sub ?? 'error');
      } catch {
        setSubscription('error');
      } finally {
        setLoading(false);
      }
    };
    load();
  }, [router]);

  useEffect(() => {
    if (subscription && subscription !== 'error' && subscription.canUseChatbot) {
      getAllChatbots()
        .then(setChatbots)
        .catch(() => setChatbots([]));
    }
  }, [subscription]);

  /** Only redirect to Stripe billing portal domains (prevent open redirect / phishing). */
  const isAllowedPortalUrl = (url: string): boolean => {
    try {
      const u = new URL(url);
      return ['billing.stripe.com', 'billing.stripe.dev'].includes(u.hostname);
    } catch {
      return false;
    }
  };

  const handleManageSubscription = async () => {
    setPortalLoading(true);
    try {
      const returnUrl = typeof window !== 'undefined' ? `${window.location.origin}/account` : undefined;
      const url = await createPortalSession(returnUrl);
      if (!url || typeof url !== 'string' || !isAllowedPortalUrl(url)) {
        alert('Invalid billing portal URL. Please try again or contact support.');
        return;
      }
      window.location.href = url;
    } catch (e) {
      const message = e instanceof Error ? e.message : 'Failed to open billing portal';
      if (!message.includes('apiKey') && !message.includes('secret') && !message.includes('stack')) {
        alert(message);
      } else {
        alert('Something went wrong. Please try again or contact support.');
      }
      if (process.env.NODE_ENV === 'development' && e instanceof Error) {
        console.error('Portal session error:', e.message);
      }
    } finally {
      setPortalLoading(false);
    }
  };

  const handleLogout = async () => {
    setLogoutLoading(true);
    try {
      await logout();
      router.replace('/');
    } catch {
      router.replace('/');
    } finally {
      setLogoutLoading(false);
    }
  };

  if (loading) {
    return (
      <main className="min-h-screen bg-gradient-to-br from-brown-900 via-brown-800 to-brown-900 flex items-center justify-center">
        <div className="flex flex-col items-center gap-4 text-brown-100">
          <Loader2 className="w-10 h-10 animate-spin" />
          <p className="text-brown-200">Loading account…</p>
        </div>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-gradient-to-br from-brown-900 via-brown-800 to-brown-900 text-brown-50">
      <div className="max-w-2xl mx-auto px-4 py-10">
        <motion.div
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3 }}
          className="mb-10"
        >
          <h1 className="text-3xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-brown-100 to-gold-200">
            Account
          </h1>
          <p className="text-brown-300 mt-1">Manage your profile and subscription</p>
        </motion.div>

        {/* Profile */}
        <motion.section
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.05 }}
          className="rounded-2xl bg-brown-800/60 backdrop-blur border border-brown-700/80 shadow-xl p-6 mb-6"
        >
          <div className="flex items-center gap-3 mb-4">
            <div className="flex-shrink-0 w-12 h-12 rounded-full overflow-hidden bg-brown-700/80 flex items-center justify-center ring-2 ring-brown-600/80">
              {user?.picture && user.picture.startsWith('https://') && user.picture.includes('googleusercontent.com') ? (
                <img
                  src={user.picture}
                  alt="Profile"
                  referrerPolicy="no-referrer"
                  className="w-full h-full object-cover"
                />
              ) : (
                <User className="w-6 h-6 text-gold-300" />
              )}
            </div>
            <h2 className="text-xl font-semibold text-brown-100">Profile</h2>
          </div>
          <dl className="space-y-3">
            <div>
              <dt className="text-sm text-brown-400 flex items-center gap-2">
                <Mail className="w-4 h-4" /> Email
              </dt>
              <dd className="text-brown-100 font-medium mt-0.5">{user?.email ?? user?.username ?? '—'}</dd>
            </div>
            <div>
              <dt className="text-sm text-brown-400">Signed in with</dt>
              <dd className="text-brown-200 mt-0.5">
                {user?.authProvider === 'GOOGLE' ? (
                  <span className="inline-flex items-center gap-2">
                    <span className="w-5 h-5 rounded-full bg-white flex items-center justify-center text-xs font-bold text-brown-800">G</span>
                    Google
                  </span>
                ) : (
                  user?.authProvider ?? '—'
                )}
              </dd>
            </div>
          </dl>
        </motion.section>

        {/* Subscription */}
        <motion.section
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.1 }}
          className="rounded-2xl bg-brown-800/60 backdrop-blur border border-brown-700/80 shadow-xl p-6 mb-6"
        >
          <div className="flex items-center gap-3 mb-4">
            <div className="p-2 rounded-xl bg-brown-700/80">
              <CreditCard className="w-6 h-6 text-gold-300" />
            </div>
            <h2 className="text-xl font-semibold text-brown-100">Subscription</h2>
          </div>
          {subscription === 'error' ? (
            <p className="text-brown-400 text-sm">Unable to load subscription. You can still manage it below.</p>
          ) : subscription ? (
            <div className="space-y-3">
              <div className="flex flex-wrap items-center gap-2">
                <span className="px-3 py-1 rounded-lg bg-brown-700/80 text-brown-100 font-medium">
                  {subscription.plan}
                </span>
                {subscription.isActive ? (
                  <span className="px-3 py-1 rounded-lg bg-emerald-900/50 text-emerald-200 text-sm">Active</span>
                ) : (
                  <span className="px-3 py-1 rounded-lg bg-brown-700/80 text-brown-300 text-sm">
                    {subscription.canceledAt ? 'Canceled' : 'Free trial'}
                  </span>
                )}
              </div>
              {subscription.currentPeriodEnd && (
                <p className="text-sm text-brown-400">
                  Next billing: {new Date(subscription.currentPeriodEnd).toLocaleDateString(undefined, {
                    dateStyle: 'medium',
                  })}
                </p>
              )}
            </div>
          ) : (
            <p className="text-brown-400 text-sm">Free trial — upgrade for more features.</p>
          )}
          <div className="mt-4 flex flex-wrap gap-3">
            <button
              onClick={handleManageSubscription}
              disabled={portalLoading}
              className="px-4 py-2.5 rounded-xl bg-gradient-to-r from-gold-700 to-gold-800 text-gold-50 font-medium hover:from-gold-600 hover:to-gold-700 transition-all flex items-center gap-2 disabled:opacity-50"
            >
              {portalLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <CreditCard className="w-4 h-4" />}
              {portalLoading ? 'Opening…' : 'Manage subscription'}
            </button>
            <Link
              href="/pricing"
              className="px-4 py-2.5 rounded-xl bg-brown-700/80 text-brown-100 font-medium hover:bg-brown-700 border border-brown-600 transition-all flex items-center gap-2"
            >
              View plans <ExternalLink className="w-4 h-4" />
            </Link>
          </div>
        </motion.section>

        {/* Embed code — only for paid subscribers */}
        {subscription && subscription !== 'error' && subscription.canUseChatbot && (
          <motion.section
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            transition={{ duration: 0.3, delay: 0.12 }}
            className="rounded-2xl bg-brown-800/60 backdrop-blur border border-brown-700/80 shadow-xl p-6 mb-6"
          >
            <div className="flex items-center gap-3 mb-4">
              <div className="p-2 rounded-xl bg-brown-700/80">
                <Code className="w-6 h-6 text-gold-300" />
              </div>
              <h2 className="text-xl font-semibold text-brown-100">Share your chatbot</h2>
            </div>
            <p className="text-brown-300 text-sm mb-4">
              Put your chatbot on your website so visitors can ask questions. As we&apos;re called to share the good news, this script brings a gentle, Christ-centered presence to your site.
            </p>
            <p className="text-brown-400 text-xs mb-4">
              Paste the code just before the closing <code className="bg-brown-900/60 px-1 rounded">&lt;/body&gt;</code> tag of your page. A chat button will appear; visitors can click to open the conversation.
            </p>
            {chatbots.length > 0 ? (
              <div className="space-y-3">
                <label className="text-sm text-brown-400 block">Choose a chatbot</label>
                <select
                  className="w-full rounded-xl bg-brown-900/60 border border-brown-600 text-brown-100 px-4 py-2.5 focus:ring-2 focus:ring-gold-500 focus:border-gold-500"
                  onChange={async (e) => {
                    const id = Number(e.target.value);
                    if (!id) {
                      setEmbedCode(null);
                      return;
                    }
                    setEmbedLoading(true);
                    setEmbedCode(null);
                    try {
                      const code = await getEmbedCode(id);
                      setEmbedCode(code);
                    } catch {
                      setEmbedCode(null);
                    } finally {
                      setEmbedLoading(false);
                    }
                  }}
                >
                  <option value="">Select…</option>
                  {chatbots.map((c) => (
                    <option key={c.id} value={c.id}>{c.name}</option>
                  ))}
                </select>
                {embedLoading && (
                  <p className="text-brown-400 text-sm flex items-center gap-2">
                    <Loader2 className="w-4 h-4 animate-spin" /> Loading code…
                  </p>
                )}
                {embedCode && (
                  <>
                    <pre className="bg-brown-900/80 p-4 rounded-lg overflow-x-auto text-brown-200 text-xs border border-brown-700 whitespace-pre-wrap break-all">
                      {embedCode}
                    </pre>
                    <button
                      type="button"
                      onClick={() => {
                        navigator.clipboard.writeText(embedCode);
                      }}
                      className="px-4 py-2.5 rounded-xl bg-gradient-to-r from-gold-700 to-gold-800 text-gold-50 font-medium flex items-center gap-2 hover:from-gold-600 hover:to-gold-700"
                    >
                      <Copy className="w-4 h-4" /> Copy code
                    </button>
                  </>
                )}
                <Link
                  href="/dashboard"
                  className="inline-flex items-center gap-2 text-gold-300 hover:text-gold-200 text-sm"
                >
                  Get embed code from Dashboard <ExternalLink className="w-4 h-4" />
                </Link>
              </div>
            ) : (
              <Link
                href="/dashboard"
                className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-brown-700/80 text-brown-100 font-medium hover:bg-brown-600"
              >
                Create a chatbot and get embed code <ExternalLink className="w-4 h-4" />
              </Link>
            )}
          </motion.section>
        )}

        {/* Security & session */}
        <motion.section
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.15 }}
          className="rounded-2xl bg-brown-800/60 backdrop-blur border border-brown-700/80 shadow-xl p-6 mb-6"
        >
          <div className="flex items-center gap-3 mb-4">
            <div className="p-2 rounded-xl bg-brown-700/80">
              <Shield className="w-6 h-6 text-gold-300" />
            </div>
            <h2 className="text-xl font-semibold text-brown-100">Security & session</h2>
          </div>
          <p className="text-brown-300 text-sm mb-4">
            You are signed in with Google. Sign out below to end this session.
          </p>
          <button
            onClick={handleLogout}
            disabled={logoutLoading}
            className="px-4 py-2.5 rounded-xl bg-brown-700/80 text-brown-100 font-medium hover:bg-brown-600 border border-brown-600 transition-all flex items-center gap-2 disabled:opacity-50"
          >
            {logoutLoading ? <Loader2 className="w-4 h-4 animate-spin" /> : <LogOut className="w-4 h-4" />}
            {logoutLoading ? 'Signing out…' : 'Sign out'}
          </button>
        </motion.section>

        {/* Links */}
        <motion.section
          initial={{ opacity: 0, y: 12 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.3, delay: 0.2 }}
          className="rounded-2xl bg-brown-800/60 backdrop-blur border border-brown-700/80 shadow-xl p-6"
        >
          <h2 className="text-lg font-semibold text-brown-100 mb-3">Legal & support</h2>
          <ul className="space-y-2">
            <li>
              <Link
                href="/privacy"
                className="text-brown-200 hover:text-gold-200 transition-colors flex items-center gap-2"
              >
                <FileText className="w-4 h-4" /> Privacy Notice
              </Link>
            </li>
            <li>
              <Link
                href="/legal"
                className="text-brown-200 hover:text-gold-200 transition-colors flex items-center gap-2"
              >
                <FileText className="w-4 h-4" /> Legal Notice
              </Link>
            </li>
            <li>
              <Link
                href="/contact"
                className="text-brown-200 hover:text-gold-200 transition-colors flex items-center gap-2"
              >
                <MessageCircle className="w-4 h-4" /> Contact
              </Link>
            </li>
          </ul>
        </motion.section>

        <div className="mt-8 text-center">
          <Link
            href="/dashboard"
            className="text-brown-400 hover:text-gold-200 text-sm transition-colors"
          >
            ← Back to Dashboard
          </Link>
        </div>
      </div>
    </main>
  );
}
