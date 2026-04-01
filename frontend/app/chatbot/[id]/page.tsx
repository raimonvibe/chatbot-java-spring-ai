'use client';

import { useState, useRef, useEffect, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useParams, useRouter } from 'next/navigation';
import Message from '@/components/Message';
import {
  sendMessage,
  getChatbot,
  getQuickReplies,
  pollUntilAnalysisReady,
  previewJesusTeachings,
  logout,
  AVATAR_IDS,
  type Message as MessageType,
  type Chatbot,
  type JesusTeachingsPreviewResponse,
} from '@/lib/api';
import Link from 'next/link';
import { BookOpen, ChevronDown, ChevronUp, Menu } from 'lucide-react';
import CalligraphicFrame from '@/components/CalligraphicFrame';
import ChatbotCreationLoader from '@/components/ChatbotCreationLoader';
import { useSetDashboardNav } from '@/context/DashboardNavContext';

/** Validates and parses chatbot ID from URL. Returns a positive integer or null if invalid (no API calls with bad ID). */
function parseChatbotId(raw: string | string[] | undefined): number | null {
  if (raw == null) return null;
  const s = typeof raw === 'string' ? raw : raw[0];
  if (s == null || s.length === 0) return null;
  const n = parseInt(s, 10);
  if (!Number.isInteger(n) || n < 1 || !Number.isFinite(n)) return null;
  return n;
}

function parseBrandingConfig(configJson: string | undefined): { primaryColor: string; secondaryColor: string; borderRadius: string } {
  const fallback = { primaryColor: '#8B5E34', secondaryColor: '#E8DCC4', borderRadius: '12px' };
  if (!configJson || !configJson.trim()) return fallback;
  if (configJson.length > 4096) return fallback;
  try {
    const o = JSON.parse(configJson) as Record<string, unknown>;
    const primaryColor = typeof o.primaryColor === 'string' ? o.primaryColor.trim() : fallback.primaryColor;
    const secondaryColor = typeof o.secondaryColor === 'string' ? o.secondaryColor.trim() : fallback.secondaryColor;
    const borderRadius = typeof o.borderRadius === 'string' ? o.borderRadius.trim() : fallback.borderRadius;
    if (!/^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(primaryColor)) return fallback;
    if (!/^#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/.test(secondaryColor)) return fallback;
    if (!/^[0-9]+(px|em|rem)?$/.test(borderRadius)) return { primaryColor, secondaryColor, borderRadius: fallback.borderRadius };
    return { primaryColor, secondaryColor, borderRadius };
  } catch {
    return fallback;
  }
}

function getHostname(url: string | undefined): string {
  if (!url) return 'your-website.com';
  try {
    return new URL(url).hostname.replace(/^www\./, '');
  } catch {
    return url.replace(/^https?:\/\//, '').split('/')[0] || 'your-website.com';
  }
}

function getSafeWebsitePreviewUrl(url: string | undefined): string | null {
  if (!url) return null;
  try {
    const parsed = new URL(url);
    const protocol = parsed.protocol.toLowerCase();
    if (protocol !== 'http:' && protocol !== 'https:') return null;
    return parsed.toString();
  } catch {
    return null;
  }
}

export default function ChatbotPreview() {
  const params = useParams();
  const router = useRouter();
  const chatbotId = useMemo(() => parseChatbotId(params?.id), [params?.id]);
  const isValidId = chatbotId !== null;
  const setNav = useSetDashboardNav();

  const [chatbot, setChatbot] = useState<Chatbot | null>(null);
  const [messages, setMessages] = useState<MessageType[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [sessionId, setSessionId] = useState<string>('');
  const [quickReplies, setQuickReplies] = useState<string[]>([]);
  const [jesusCardOpen, setJesusCardOpen] = useState(false);
  const [jesusActiveTab, setJesusActiveTab] = useState<'verse' | 'teachings'>('verse');
  const [jesusPreview, setJesusPreview] = useState<JesusTeachingsPreviewResponse | null>(null);
  const [jesusPreviewError, setJesusPreviewError] = useState<string | null>(null);
  const [jesusPreviewLoading, setJesusPreviewLoading] = useState(false);
  /** When true, we are still waiting for website analysis so the chatbot can answer about the site. */
  const [analysisLoading, setAnalysisLoading] = useState(true);
  const [screenPreview, setScreenPreview] = useState<'desktop' | 'tablet' | 'mobile'>('desktop');
  const [previewMode, setPreviewMode] = useState<'fit' | 'actual'>('fit');
  const [sceneMode, setSceneMode] = useState<'plain' | 'website'>('plain');
  const [isWidgetOpen, setIsWidgetOpen] = useState(true);
  const [showScreenMenu, setShowScreenMenu] = useState(false);
  const [websiteFrameLoaded, setWebsiteFrameLoaded] = useState(false);
  const [websiteFrameLikelyBlocked, setWebsiteFrameLikelyBlocked] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const previewScrollRef = useRef<HTMLDivElement>(null);
  const theme = useMemo(() => parseBrandingConfig(chatbot?.brandingConfig), [chatbot?.brandingConfig]);
  const SCREEN_WIDTHS: Record<'desktop' | 'tablet' | 'mobile', number> = {
    desktop: 1024,
    tablet: 768,
    mobile: 390,
  };

  useEffect(() => {
    if (!isValidId || chatbotId === null) return;
    let cancelled = false;
    getChatbot(chatbotId)
      .then(async (data) => {
        if (cancelled) return;
        setChatbot(data);
        setMessages([
          {
            id: '1',
            role: 'assistant',
            content: `Hello! I'm ${data.name}. ${data.description}`,
            timestamp: Date.now(),
          },
        ]);
        // If chatbot has a website, keep loading until analysis is ready so "tell me about this site" works
        if (data.websiteUrl?.trim()) {
          try {
            await pollUntilAnalysisReady(chatbotId);
          } finally {
            if (!cancelled) setAnalysisLoading(false);
          }
        } else {
          setAnalysisLoading(false);
        }
        // If \"What Jesus Would Say\" is enabled, load a small preview of teachings for the header card
        if (data.jesusTeachingsEnabled) {
          setJesusPreviewLoading(true);
          setJesusPreviewError(null);
          previewJesusTeachings(chatbotId, 3)
            .then(setJesusPreview)
            .catch((err) => {
              console.error(err);
              setJesusPreviewError('Could not load Jesus teachings preview.');
            })
            .finally(() => {
              setJesusPreviewLoading(false);
            });
        }
      })
      .catch((err) => {
        console.error(err);
        if (!cancelled) setAnalysisLoading(false);
      });

    getQuickReplies(chatbotId)
      .then(setQuickReplies)
      .catch(console.error);

    return () => { cancelled = true; };
  }, [chatbotId, isValidId]);

  // Provide nav context so header shows Dashboard/Account/Subscription/Logout and mobile menu on preview page
  useEffect(() => {
    if (!isValidId) {
      setNav(null);
      return;
    }
    const handleLogout = async () => {
      try {
        await logout();
        router.replace('/');
        if (typeof window !== 'undefined') window.location.href = '/';
      } catch {
        router.replace('/');
        if (typeof window !== 'undefined') window.location.href = '/';
      }
    };
    setNav({
      openSubscription: async () => { router.push('/dashboard'); },
      logout: handleLogout,
      toggleCreateForm: () => router.push('/dashboard'),
      showCreateForm: false,
      hasChatbots: true,
      isPreviewMode: false,
      onDeleteAllChatbots: () => {},
      portalLoading: false,
    });
    return () => setNav(null);
  }, [isValidId, setNav, router]);

  // Scroll messages container to bottom when messages change (sends/receives)
  useEffect(() => {
    const el = messagesContainerRef.current;
    if (!el) return;
    const scrollToBottom = () => {
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
    };
    scrollToBottom();
    // After layout (new message in DOM), scroll again in case height wasn't ready
    const id = requestAnimationFrame(() => {
      requestAnimationFrame(scrollToBottom);
    });
    return () => cancelAnimationFrame(id);
  }, [messages]);

  const handleSendMessage = async (messageText?: string) => {
    const messageToSend = (messageText ?? input).trim();
    if (!messageToSend || isLoading || !isValidId || chatbotId === null) return;

    const userMessage: MessageType = {
      id: Date.now().toString(),
      role: 'user',
      content: messageToSend,
      timestamp: Date.now(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setIsLoading(true);

    try {
      const userLanguage = typeof navigator !== 'undefined' ? (navigator.language?.split('-')[0] || 'en') : 'en';
      const response = await sendMessage(chatbotId as number, messageToSend, sessionId, userLanguage);

      if (response.sessionId && !sessionId) {
        setSessionId(response.sessionId);
      }

      const assistantMessage: MessageType = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: response.message,
        timestamp: response.timestamp,
      };

      setMessages((prev) => [...prev, assistantMessage]);
    } catch (error) {
      console.error('Error sending message:', error);
      const errorMsg = error instanceof Error ? error.message : 'Unknown error occurred';
      const errorMessage: MessageType = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: `Sorry, I encountered an error: ${errorMsg}. Please try again.`,
        timestamp: Date.now(),
      };
      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  const hasJesusFeature = chatbot?.jesusTeachingsEnabled || chatbot?.bibleVerse;
  const selectedScreenWidth = SCREEN_WIDTHS[screenPreview];
  const isMobilePreview = screenPreview === 'mobile';
  const websiteHost = getHostname(chatbot?.websiteUrl);
  const websitePreviewUrl = getSafeWebsitePreviewUrl(chatbot?.websiteUrl);

  useEffect(() => {
    if (sceneMode !== 'website') return;
    setWebsiteFrameLoaded(false);
    setWebsiteFrameLikelyBlocked(false);
    const id = setTimeout(() => {
      setWebsiteFrameLikelyBlocked(true);
    }, 5000);
    return () => clearTimeout(id);
  }, [sceneMode, chatbot?.websiteUrl]);

  useEffect(() => {
    if (previewMode !== 'actual') return;
    if (screenPreview === 'mobile') return;
    const el = previewScrollRef.current;
    if (!el) return;
    // Desktop/tablet widget is right-aligned inside a wider canvas; auto-pan there on narrow devices.
    const maxScroll = Math.max(0, el.scrollWidth - el.clientWidth);
    el.scrollLeft = maxScroll;
  }, [previewMode, screenPreview]);

  if (!isValidId) {
    return (
      <main className="h-screen flex flex-col items-center justify-center bg-gradient-to-br from-brown-50 via-amber-50/30 to-gold-50 p-4">
        <div className="text-center max-w-md">
          <h1 className="text-xl font-bold text-brown-800 mb-2">Invalid chatbot</h1>
          <p className="text-brown-700 mb-4">This link is not valid. Please use a link from your dashboard.</p>
          <Link
            href="/dashboard"
            className="inline-block px-4 py-2 bg-brown-200 text-brown-800 rounded-lg hover:bg-brown-300 transition-colors"
          >
            Back to Dashboard
          </Link>
        </div>
      </main>
    );
  }

  if (analysisLoading) {
    return (
      <>
        <ChatbotCreationLoader isVisible={true} mode="analysis" chatbotName={chatbot?.name} isScanningWebsite />
      </>
    );
  }

  return (
    <main
      className="h-[100dvh] min-h-[100dvh] flex flex-col overflow-hidden"
      style={{
        background: `linear-gradient(135deg, ${theme.secondaryColor}22 0%, #ffffff 45%, ${theme.primaryColor}18 100%)`,
      }}
    >
      {/* Compact header; on desktop the whole page scrolls so the chat area is taller */}
      <header className="flex-shrink-0 p-2 md:p-3 border-b border-brown-200/60 bg-white/50 backdrop-blur-sm">
        <div className="max-w-4xl mx-auto flex flex-wrap items-center justify-between gap-2">
          <div className="min-w-0 flex-1 flex items-center gap-3">
            {chatbot?.avatarId && AVATAR_IDS.includes(chatbot.avatarId as (typeof AVATAR_IDS)[number]) && (
              <div className="flex-shrink-0 w-12 h-12 md:w-14 md:h-14 rounded-full overflow-hidden border-2 border-brown-200 bg-brown-100">
                {/* eslint-disable-next-line @next/next/no-img-element */}
                <img
                  src={`/${chatbot.avatarId}.png`}
                  alt=""
                  role="presentation"
                  className="w-full h-full object-cover"
                />
              </div>
            )}
            <div className="min-w-0 flex-1">
              <h1
                className="text-xl md:text-2xl font-bold truncate"
                style={{ color: theme.primaryColor }}
              >
                {chatbot?.name ?? 'Loading...'}
              </h1>
              <p className="text-brown-700 text-sm truncate max-w-[min(100%,320px)] md:max-w-none">
                {chatbot?.description}
              </p>
            </div>
          </div>
          <Link
            href="/dashboard"
            className="flex-shrink-0 px-3 py-1.5 md:px-4 md:py-2 text-sm rounded-lg transition-colors border border-brown-200"
            style={{ backgroundColor: `${theme.secondaryColor}66`, color: '#4a3828' }}
          >
            Back to Dashboard
          </Link>
        </div>
      </header>

      {/* Optional Jesus card: collapsible, compact so chat keeps most of the height */}
      {hasJesusFeature && (
        <motion.div
          initial={false}
          className="flex-shrink-0 border-b border-brown-200/60 bg-gradient-to-r from-brown-50/80 to-amber-50/50"
        >
          <button
            type="button"
            onClick={() => setJesusCardOpen((o) => !o)}
            className="w-full flex items-center gap-3 px-4 py-2 text-left hover:bg-brown-100/50 transition-colors"
          >
            <div className="flex-shrink-0 w-9 h-9 rounded-lg bg-gradient-to-br from-brown-500 to-gold-600 flex items-center justify-center">
              <BookOpen className="w-5 h-5 text-white" strokeWidth={2} />
            </div>
            <span className="text-sm font-semibold text-brown-800">Jesus-inspired guidance</span>
            {jesusCardOpen ? (
              <ChevronUp className="w-4 h-4 text-brown-600 ml-auto" />
            ) : (
              <ChevronDown className="w-4 h-4 text-brown-600 ml-auto" />
            )}
          </button>
          <AnimatePresence>
            {jesusCardOpen && (
              <motion.div
                initial={{ height: 0, opacity: 0 }}
                animate={{ height: 'auto', opacity: 1 }}
                exit={{ height: 0, opacity: 0 }}
                transition={{ duration: 0.2 }}
                className="overflow-hidden"
              >
                <div className="px-4 pb-3 pt-1 text-sm text-brown-700">
                  <p className="mb-3 max-w-3xl text-xs md:text-sm">
                    This chatbot weaves in gentle inspiration from Jesus&apos;s teachings and relevant Bible verses.
                  </p>

                  {/* Tabs */}
                  <div className="flex flex-wrap gap-2 mb-3">
                    <button
                      type="button"
                      onClick={() => setJesusActiveTab('verse')}
                      className={`px-3 py-1.5 text-xs rounded-full border ${
                        jesusActiveTab === 'verse'
                          ? 'bg-brown-700 text-white border-brown-700'
                          : 'bg-white text-brown-700 border-brown-200 hover:bg-brown-50'
                      }`}
                    >
                      Related verse
                    </button>
                    <button
                      type="button"
                      onClick={() => setJesusActiveTab('teachings')}
                      className={`px-3 py-1.5 text-xs rounded-full border ${
                        jesusActiveTab === 'teachings'
                          ? 'bg-brown-700 text-white border-brown-700'
                          : 'bg-white text-brown-700 border-brown-200 hover:bg-brown-50'
                      }`}
                    >
                      What Jesus would say
                    </button>
                  </div>

                  {/* Tab content container with max height for responsiveness */}
                  <div className="max-h-40 md:max-h-48 overflow-y-auto pr-1 space-y-2">
                    {jesusActiveTab === 'verse' ? (
                      chatbot?.bibleVerse ? (
                        <blockquote className="pl-3 border-l-4 border-gold-500 italic bg-white/70 rounded-r-lg py-1.5 pr-3 text-xs md:text-sm">
                          {chatbot.bibleVerse}
                        </blockquote>
                      ) : (
                        <p className="text-[11px] md:text-xs text-brown-600">
                          No specific verse has been attached yet. On your dashboard, run the Christian Content analysis
                          to generate a verse connected to this site.
                        </p>
                      )
                    ) : jesusPreviewLoading ? (
                      <p className="text-[11px] md:text-xs text-brown-600">Loading teachings…</p>
                    ) : jesusPreviewError ? (
                      <p className="text-[11px] md:text-xs text-red-600">{jesusPreviewError}</p>
                    ) : jesusPreview && jesusPreview.topTeachings.length > 0 ? (
                      <>
                        {jesusPreview.topTeachings.slice(0, 2).map((t, idx) => (
                          <div
                            key={`${t.reference}-${idx}`}
                            className="bg-white/80 rounded-lg border border-brown-200 px-3 py-2"
                          >
                            <div className="text-xs font-semibold text-gold-800">{t.reference}</div>
                            <div className="text-[11px] md:text-xs text-brown-700 mt-1 line-clamp-3">{t.text}</div>
                          </div>
                        ))}
                        {jesusPreview.topTeachings.length > 2 && (
                          <p className="text-[11px] text-brown-600 mt-1">
                            More teachings are available in your dashboard under &quot;What Jesus Would Say&quot;.
                          </p>
                        )}
                      </>
                    ) : (
                      <p className="text-[11px] md:text-xs text-brown-600">
                        No specific teachings preview is available yet. You can enable and preview &quot;What Jesus
                        Would Say&quot; from the dashboard settings.
                      </p>
                    )}
                  </div>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>
      )}

      {/* Screen-size preview controls */}
      <div className="w-full max-w-4xl mx-auto px-2 md:px-3 pt-2">
        <div className="rounded-xl bg-white/70 border border-brown-200 p-1.5">
          <div className="flex items-center justify-center gap-2 pb-1.5 mb-1.5 border-b border-brown-200/80">
            {(['fit', 'actual'] as const).map((mode) => (
              <button
                key={mode}
                type="button"
                onClick={() => setPreviewMode(mode)}
                className="px-3 py-1.5 text-xs md:text-sm rounded-lg border transition-colors"
                style={{
                  backgroundColor: previewMode === mode ? theme.primaryColor : '#ffffff',
                  color: previewMode === mode ? '#ffffff' : '#5b4634',
                  borderColor: previewMode === mode ? theme.primaryColor : '#e8d9c9',
                }}
              >
                {mode === 'fit' ? 'Fit to screen' : 'Actual size'}
              </button>
            ))}
          </div>
          <div className="flex items-center justify-center gap-2 pb-1.5 mb-1.5 border-b border-brown-200/80">
            {(['plain', 'website'] as const).map((mode) => (
              <button
                key={mode}
                type="button"
                onClick={() => setSceneMode(mode)}
                className="px-3 py-1.5 text-xs md:text-sm rounded-lg border transition-colors"
                style={{
                  backgroundColor: sceneMode === mode ? theme.primaryColor : '#ffffff',
                  color: sceneMode === mode ? '#ffffff' : '#5b4634',
                  borderColor: sceneMode === mode ? theme.primaryColor : '#e8d9c9',
                }}
                disabled={mode === 'website' && !websitePreviewUrl}
                title={mode === 'website' && !websitePreviewUrl ? 'No safe website URL on this chatbot' : undefined}
              >
                {mode === 'plain' ? 'Plain background' : 'Website background'}
              </button>
            ))}
          </div>
          <div className="hidden md:flex items-center justify-center gap-2">
            {(['desktop', 'tablet', 'mobile'] as const).map((size) => (
              <button
                key={size}
                type="button"
                onClick={() => setScreenPreview(size)}
                className="px-3 py-1.5 text-xs md:text-sm rounded-lg border transition-colors"
                style={{
                  backgroundColor: screenPreview === size ? theme.primaryColor : '#ffffff',
                  color: screenPreview === size ? '#ffffff' : '#5b4634',
                  borderColor: screenPreview === size ? theme.primaryColor : '#e8d9c9',
                }}
              >
                {size === 'desktop' ? 'Desktop' : size === 'tablet' ? 'Tablet' : 'Mobile'}
              </button>
            ))}
          </div>
          <div className="md:hidden relative">
            <button
              type="button"
              onClick={() => setShowScreenMenu((v) => !v)}
              className="w-full px-3 py-2 text-sm rounded-lg border bg-white text-brown-800 border-brown-200 flex items-center justify-between"
            >
              <span className="flex items-center gap-2">
                <Menu className="w-4 h-4" />
                Screen size: {screenPreview === 'desktop' ? 'Desktop' : screenPreview === 'tablet' ? 'Tablet' : 'Mobile'}
              </span>
              <ChevronDown className={`w-4 h-4 transition-transform ${showScreenMenu ? 'rotate-180' : ''}`} />
            </button>
            {showScreenMenu && (
              <div className="absolute z-20 mt-1 w-full rounded-lg border border-brown-200 bg-white shadow-lg p-1">
                {(['desktop', 'tablet', 'mobile'] as const).map((size) => (
                  <button
                    key={size}
                    type="button"
                    onClick={() => {
                      setScreenPreview(size);
                      setShowScreenMenu(false);
                    }}
                    className="w-full text-left px-3 py-2 text-sm rounded-md hover:bg-brown-50"
                    style={{ color: screenPreview === size ? theme.primaryColor : '#5b4634' }}
                  >
                    {size === 'desktop' ? 'Desktop' : size === 'tablet' ? 'Tablet' : 'Mobile'}
                  </button>
                ))}
              </div>
            )}
          </div>
          <p className="text-[11px] text-brown-600 mt-1 px-1 md:hidden">
            {previewMode === 'actual'
              ? 'For desktop/tablet previews on phone, swipe horizontally in the preview area.'
              : 'Fit mode scales the preview to your current screen width.'}
          </p>
        </div>
      </div>

      {/* Chat window: simulate real embed placement per viewport */}
      <div className="flex-1 min-h-0 w-full p-2 md:p-3">
        <div ref={previewScrollRef} className={`h-full w-full ${previewMode === 'actual' ? 'overflow-x-auto' : 'overflow-x-hidden'}`}>
          <div
            className="h-full mx-auto transition-all duration-200"
            style={
              previewMode === 'actual'
                ? { width: `${selectedScreenWidth}px`, minWidth: `${selectedScreenWidth}px` }
                : { width: '100%', maxWidth: '100%', minWidth: '0' }
            }
          >
            <div className="h-full w-full relative rounded-2xl border border-brown-200/80 overflow-hidden bg-gradient-to-br from-white via-brown-50/30 to-amber-50/40">
              {sceneMode === 'website' && websitePreviewUrl && (
                <iframe
                  src={websitePreviewUrl}
                  title="Website preview background"
                  className="absolute inset-0 w-full h-full"
                  sandbox="allow-scripts allow-forms allow-popups"
                  referrerPolicy="no-referrer"
                  loading="lazy"
                  onLoad={() => setWebsiteFrameLoaded(true)}
                />
              )}
              {sceneMode === 'website' && websiteFrameLikelyBlocked && !websiteFrameLoaded && (
                <div className="absolute inset-0 bg-white/95">
                  <div className="h-full w-full p-3 md:p-5 flex flex-col gap-3 md:gap-4">
                    <div className="h-12 rounded-xl border border-brown-200/80 bg-white flex items-center px-3 md:px-4">
                      <div
                        className="w-8 h-8 rounded-lg mr-3 flex items-center justify-center text-white text-xs font-bold"
                        style={{ backgroundColor: theme.primaryColor }}
                      >
                        {websiteHost.charAt(0).toUpperCase()}
                      </div>
                      <div className="flex-1 min-w-0">
                        <div className="text-xs md:text-sm font-semibold text-brown-800 truncate">{websiteHost}</div>
                        <div className="text-[11px] text-brown-500 truncate">Fallback preview layout (iframe blocked)</div>
                      </div>
                    </div>

                    <div
                      className="rounded-2xl border border-brown-200/70 p-4 md:p-6"
                      style={{ background: `linear-gradient(120deg, ${theme.secondaryColor}33 0%, #ffffff 70%)` }}
                    >
                      <div className="text-sm md:text-base font-semibold text-brown-900 mb-2 truncate">
                        {chatbot?.name ?? 'Your chatbot'} on {websiteHost}
                      </div>
                      <div className="text-xs md:text-sm text-brown-700 line-clamp-2">
                        {chatbot?.description || 'This area simulates a website hero section so you can validate chatbot placement and style even when live framing is blocked.'}
                      </div>
                      <div className="mt-3 flex gap-2">
                        <div className="h-7 rounded-full w-24" style={{ backgroundColor: `${theme.primaryColor}22` }} />
                        <div className="h-7 rounded-full w-20 bg-brown-100" />
                      </div>
                    </div>

                    <div className="grid grid-cols-1 md:grid-cols-2 gap-3 md:gap-4 flex-1 min-h-0">
                      <div className="rounded-xl border border-brown-200/70 bg-white p-3 md:p-4">
                        <div className="h-3 w-28 rounded bg-brown-200 mb-2" />
                        <div className="h-2.5 w-full rounded bg-brown-100 mb-1.5" />
                        <div className="h-2.5 w-[85%] rounded bg-brown-100 mb-1.5" />
                        <div className="h-2.5 w-[70%] rounded bg-brown-100" />
                      </div>
                      <div className="rounded-xl border border-brown-200/70 bg-white p-3 md:p-4">
                        <div className="h-3 w-32 rounded bg-brown-200 mb-2" />
                        <div className="h-2.5 w-full rounded bg-brown-100 mb-1.5" />
                        <div className="h-2.5 w-[80%] rounded bg-brown-100 mb-1.5" />
                        <div className="h-2.5 w-[75%] rounded bg-brown-100" />
                      </div>
                    </div>

                    <div className="text-[11px] text-brown-600">
                      Tip: some sites deny iframe embedding via security headers. Optional future upgrade: backend-generated website screenshot fallback.
                    </div>
                  </div>
                </div>
              )}
              {sceneMode === 'website' && (
                <div className="absolute inset-0 bg-white/40 pointer-events-none" />
              )}
              <div className="absolute inset-0 pointer-events-none">
                <div className="w-full h-12 border-b border-brown-100/80 bg-white/60" />
              </div>
              {sceneMode === 'website' && websiteFrameLikelyBlocked && !websiteFrameLoaded && (
                <div className="absolute top-2 left-1/2 -translate-x-1/2 z-20 px-3 py-1 rounded-full bg-amber-100 border border-amber-300 text-amber-800 text-xs">
                  Website blocked iframe preview. Showing widget only.
                </div>
              )}

              {isWidgetOpen && (
                <div
                  data-testid="preview-widget-panel"
                  className="absolute shadow-2xl border border-brown-200/80 bg-white/95 backdrop-blur-sm overflow-hidden"
                  style={
                    isMobilePreview
                      ? {
                          // Embed uses 50dvh vs the real viewport (chatbot-widget.js). The dashboard preview nests
                          // the panel inside a shorter frame; pure 50dvh fills ~most of that frame. Use 50% of the
                          // preview scene with the same max as production so website background stays visible.
                          left: '2.5%',
                          right: '2.5%',
                          bottom: 'max(12px, env(safe-area-inset-bottom))',
                          width: '95%',
                          height: '50%',
                          maxHeight: '50dvh',
                          minHeight: 200,
                          borderRadius: 16,
                        }
                      : {
                          width: 350,
                          height: 500,
                          right: 20,
                          bottom: 20,
                          borderRadius: parseInt(theme.borderRadius, 10) > 0 ? parseInt(theme.borderRadius, 10) : 12,
                        }
                  }
                >
                  <div className="h-full flex flex-col overflow-hidden">
                    <div className="flex items-center justify-between px-[15px] py-[15px] text-white" style={{ backgroundColor: theme.primaryColor }}>
                      <div className="font-semibold truncate">{chatbot?.name ?? 'AI Assistant'}</div>
                      <button
                        type="button"
                        onClick={() => setIsWidgetOpen(false)}
                        className="text-white/90 hover:text-white text-lg leading-none"
                        aria-label="Close widget preview"
                      >
                        ×
                      </button>
                    </div>

                    <div
                      ref={messagesContainerRef}
                      className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden px-[15px] py-[15px] bg-gradient-to-b from-brown-50/40 to-gold-50/30 custom-scrollbar"
                    >
                      <AnimatePresence mode="popLayout">
                        {messages.map((message, index) => (
                          <Message
                            key={message.id}
                            message={message}
                            index={index}
                            primaryColor={theme.primaryColor}
                            secondaryColor={theme.secondaryColor}
                            assistantAvatarId={chatbot?.avatarId}
                          />
                        ))}
                      </AnimatePresence>
                      {isLoading && (
                        <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="flex justify-start mb-4">
                          <div className="rounded-2xl px-4 py-3 shadow-md border" style={{ backgroundColor: `${theme.secondaryColor}55`, borderColor: `${theme.secondaryColor}aa` }}>
                            <div className="flex space-x-2">
                              {[0, 1, 2].map((i) => (
                                <motion.div
                                  key={i}
                                  className="w-2 h-2 rounded-full"
                                  style={{ backgroundColor: theme.primaryColor }}
                                  animate={{ scale: [1, 1.2, 1], opacity: [0.7, 1, 0.7] }}
                                  transition={{ duration: 0.6, repeat: Infinity, delay: i * 0.2 }}
                                />
                              ))}
                            </div>
                          </div>
                        </motion.div>
                      )}
                      <div ref={messagesEndRef} />
                    </div>

                    {quickReplies.length > 0 && (
                      <div className="flex-shrink-0 px-[15px] py-2 border-t border-brown-200/80 bg-brown-50/60">
                        <div className="flex flex-wrap gap-2">
                          {quickReplies.map((reply, index) => (
                            <button
                              key={index}
                              onClick={() => handleSendMessage(reply)}
                              className="px-3 py-1.5 text-xs rounded-full transition-colors border"
                              style={{ backgroundColor: `${theme.secondaryColor}66`, color: '#4a3828', borderColor: `${theme.secondaryColor}aa` }}
                              disabled={isLoading}
                            >
                              {reply}
                            </button>
                          ))}
                        </div>
                      </div>
                    )}

                    <div className="flex-shrink-0 px-[15px] py-[15px] border-t border-brown-200/80 bg-white">
                      <div className="flex gap-[10px] min-w-0 items-center">
                        <input
                          type="text"
                          value={input}
                          onChange={(e) => setInput(e.target.value)}
                          onKeyPress={handleKeyPress}
                          placeholder="Type your message..."
                          disabled={isLoading}
                          className="min-w-0 flex-1 px-3 py-2 rounded-[20px] border focus:outline-none focus:ring-2 disabled:opacity-50 bg-white text-brown-900 placeholder:text-brown-400 text-sm"
                          style={{ borderColor: `${theme.secondaryColor}cc` }}
                        />
                        <button
                          onClick={() => handleSendMessage()}
                          disabled={!input.trim() || isLoading}
                          className="flex-shrink-0 text-white rounded-full font-medium disabled:opacity-50 hover:shadow-lg transition-all w-10 h-10 min-w-[40px] min-h-[40px] flex items-center justify-center"
                          style={{ backgroundColor: theme.primaryColor }}
                          aria-label="Send message"
                        >
                          <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" className="w-4 h-4">
                            <path strokeLinecap="round" strokeLinejoin="round" d="M6 12L3.269 3.126A59.768 59.768 0 0121.485 12 59.77 59.77 0 013.27 20.876L5.999 12zm0 0h7.5" />
                          </svg>
                        </button>
                      </div>
                    </div>
                  </div>
                </div>
              )}

              {!isWidgetOpen && (
                <button
                  data-testid="preview-widget-toggle"
                  type="button"
                  onClick={() => setIsWidgetOpen(true)}
                  className="absolute text-white rounded-full shadow-xl hover:scale-105 transition-transform flex items-center justify-center"
                  style={{
                    width: isMobilePreview ? 50 : 60,
                    height: isMobilePreview ? 50 : 60,
                    right: isMobilePreview ? 12 : 20,
                    bottom: isMobilePreview ? 'max(12px, env(safe-area-inset-bottom))' : 20,
                    backgroundColor: theme.primaryColor,
                  }}
                  aria-label="Open widget preview"
                >
                  💬
                </button>
              )}
            </div>
            <p className="mt-2 text-[11px] md:text-xs text-brown-600">
              Preview limitations: some websites block iframe embedding with security headers. In that case, website background cannot be shown,
              but widget size/position/theme simulation remains accurate.
            </p>
          </div>
        </div>
      </div>
    </main>
  );
}
