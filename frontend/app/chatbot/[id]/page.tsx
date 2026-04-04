'use client';

import { useState, useRef, useEffect, useLayoutEffect, useMemo, useCallback } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useParams, usePathname, useRouter } from 'next/navigation';
import Message from '@/components/Message';
import {
  sendMessage,
  getChatbot,
  getQuickReplies,
  getAnalysisStatus,
  pollUntilAnalysisReady,
  previewJesusTeachings,
  logout,
  getSubscriptionStatusFromApi,
  AVATAR_IDS,
  getUserFacingFetchError,
  logClientIssue,
  type Message as MessageType,
  type Chatbot,
  type JesusTeachingsPreviewResponse,
  type AnalysisStatus,
} from '@/lib/api';
import Link from 'next/link';
import { ChevronDown, GripHorizontal, MessageCircle } from 'lucide-react';
import ChatbotCreationLoader from '@/components/ChatbotCreationLoader';
import JesusGuidanceCard from '@/components/JesusGuidanceCard';
import { useSetDashboardNav } from '@/context/DashboardNavContext';
import { useChatbotPreviewControlsRegistration } from '@/context/ChatbotPreviewControlsContext';
import { isBillingEnabledFromEnv, paymentActionsAvailableFromApi } from '@/lib/billing-config';

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
  if (!url?.trim()) return null;
  const trimmed = url.trim();
  const withScheme = /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
  try {
    const parsed = new URL(withScheme);
    const protocol = parsed.protocol.toLowerCase();
    if (protocol !== 'http:' && protocol !== 'https:') return null;
    if (!parsed.hostname) return null;
    return parsed.toString();
  } catch {
    return null;
  }
}

function chatbotIdMatchesRoute(chatbot: Chatbot, routeId: number): boolean {
  const cid = typeof chatbot.id === 'number' ? chatbot.id : Number(chatbot.id);
  return Number.isFinite(cid) && cid === routeId;
}

export default function ChatbotPreview() {
  const params = useParams();
  const pathname = usePathname();
  const router = useRouter();
  const chatbotId = useMemo(() => parseChatbotId(params?.id), [params?.id]);
  const isValidId = chatbotId !== null;
  const setNav = useSetDashboardNav();
  const { setControls: setPreviewToolbarControls } = useChatbotPreviewControlsRegistration();

  const [chatbot, setChatbot] = useState<Chatbot | null>(null);
  const [messages, setMessages] = useState<MessageType[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [sessionId, setSessionId] = useState<string>('');
  const [quickReplies, setQuickReplies] = useState<string[]>([]);
  const [jesusPreview, setJesusPreview] = useState<JesusTeachingsPreviewResponse | null>(null);
  const [jesusPreviewError, setJesusPreviewError] = useState<string | null>(null);
  const [jesusPreviewLoading, setJesusPreviewLoading] = useState(false);
  /** When true, we are still waiting for website analysis so the chatbot can answer about the site. */
  const [analysisLoading, setAnalysisLoading] = useState(true);
  const [screenPreview, setScreenPreview] = useState<'desktop' | 'tablet' | 'mobile'>('desktop');
  const [previewMode, setPreviewMode] = useState<'fit' | 'actual'>('actual');
  const [sceneMode, setSceneMode] = useState<'plain' | 'website'>('plain');
  const [isWidgetOpen, setIsWidgetOpen] = useState(true);
  const [websiteFrameLoaded, setWebsiteFrameLoaded] = useState(false);
  const [websiteFrameLikelyBlocked, setWebsiteFrameLikelyBlocked] = useState(false);
  /** After hardening (safe URL only), default was plain; users expect website + widget on first open when URL exists. */
  const sceneDefaultAppliedRef = useRef(false);
  const [showSubscriptionNav, setShowSubscriptionNav] = useState(() => isBillingEnabledFromEnv());
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const previewScrollRef = useRef<HTMLDivElement>(null);
  const previewDeviceFrameRef = useRef<HTMLDivElement>(null);
  const previewWidgetPanelRef = useRef<HTMLDivElement>(null);
  const previewResizeDragRef = useRef<{ startY: number; startH: number; pointerId: number } | null>(null);
  const [previewFrameHeight, setPreviewFrameHeight] = useState(0);
  /** User-adjusted panel height (px); null = use default responsive embed styles */
  const [previewWidgetHeightPx, setPreviewWidgetHeightPx] = useState<number | null>(null);
  const brandingJson = typeof chatbot?.brandingConfig === 'string' ? chatbot.brandingConfig : '';
  const theme = useMemo(() => parseBrandingConfig(brandingJson || undefined), [brandingJson]);
  const SCREEN_WIDTHS: Record<'desktop' | 'tablet' | 'mobile', number> = {
    desktop: 1024,
    tablet: 768,
    mobile: 390,
  };

  useEffect(() => {
    getSubscriptionStatusFromApi()
      .then((s) => setShowSubscriptionNav(paymentActionsAvailableFromApi(s)))
      .catch(() => setShowSubscriptionNav(isBillingEnabledFromEnv()));
  }, []);

  /**
   * Narrow viewports: mobile frame + fit width so the mock phone and chat sheet fit the screen (no 390px overflow).
   */
  useLayoutEffect(() => {
    if (typeof window === 'undefined') return;
    if (!window.matchMedia('(max-width: 767px)').matches) return;
    setScreenPreview('mobile');
    setPreviewMode('fit');
  }, []);

  // pathname in deps so revisiting /chatbot/:id after dashboard edits refetches (avoids stale cached GET for branding/avatar).
  useEffect(() => {
    if (!isValidId || chatbotId === null) return;
    let cancelled = false;
    // Never treat a failed status request as "not ready" — that forces up to 2min of polling even when already indexed.
    Promise.allSettled([getChatbot(chatbotId), getAnalysisStatus(chatbotId)])
      .then(async (results) => {
        if (cancelled) return;
        const chatResult = results[0];
        const statusResult = results[1];
        if (chatResult.status === 'rejected') {
          logClientIssue('chatbotPreview.load', chatResult.reason);
          setAnalysisLoading(false);
          return;
        }
        const data = chatResult.value;
        setChatbot(data);
        setMessages([
          {
            id: '1',
            role: 'assistant',
            content: `Hello! I'm ${data.name}. ${data.description}`,
            timestamp: Date.now(),
          },
        ]);
        // Show preview + widget as soon as chatbot loads. Do not block the whole page on background indexing
        // (poll can run minutes; users reported an empty preview when stuck behind ChatbotCreationLoader).
        if (!cancelled) setAnalysisLoading(false);

        if (data.websiteUrl?.trim()) {
          void (async () => {
            try {
              let statusSnapshot: AnalysisStatus =
                statusResult.status === 'fulfilled'
                  ? statusResult.value
                  : { ready: false, pagesIndexed: 0 };
              if (statusResult.status === 'rejected') {
                try {
                  statusSnapshot = await getAnalysisStatus(chatbotId);
                } catch {
                  /* still not ready → poll */
                }
              }
              if (!statusSnapshot.ready) {
                await pollUntilAnalysisReady(chatbotId);
              }
            } catch (e) {
              logClientIssue('chatbotPreview.backgroundAnalysisPoll', e);
            }
          })();
        }
        // If \"What Jesus Would Say\" is enabled, load a small preview of teachings for the header card
        if (data.jesusTeachingsEnabled) {
          setJesusPreviewLoading(true);
          setJesusPreviewError(null);
          previewJesusTeachings(chatbotId, 3)
            .then(setJesusPreview)
            .catch((err) => {
              logClientIssue('chatbotPreview.jesusPreview', err);
              setJesusPreviewError('Could not load Jesus teachings preview.');
            })
            .finally(() => {
              setJesusPreviewLoading(false);
            });
        }
      })
      .catch((err) => {
        logClientIssue('chatbotPreview.load', err);
        if (!cancelled) setAnalysisLoading(false);
      });

    getQuickReplies(chatbotId)
      .then(setQuickReplies)
      .catch((e) => logClientIssue('chatbotPreview.quickReplies', e));

    return () => {
      cancelled = true;
    };
  }, [chatbotId, isValidId, pathname]);

  /** If user returns to this tab after editing on another tab, pick up latest branding/avatar */
  useEffect(() => {
    if (!isValidId || chatbotId === null) return;
    const onVisible = () => {
      if (document.visibilityState !== 'visible') return;
      getChatbot(chatbotId)
        .then((data) => {
          if (!chatbotIdMatchesRoute(data, chatbotId)) return;
          setChatbot(data);
        })
        .catch((e) => logClientIssue('chatbotPreview.refetchOnVisible', e));
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, [chatbotId, isValidId]);

  useEffect(() => {
    sceneDefaultAppliedRef.current = false;
  }, [chatbotId]);

  useEffect(() => {
    if (chatbotId == null || !chatbot || !chatbotIdMatchesRoute(chatbot, chatbotId)) return;
    if (sceneDefaultAppliedRef.current) return;
    const safe = getSafeWebsitePreviewUrl(chatbot.websiteUrl);
    setSceneMode(safe ? 'website' : 'plain');
    sceneDefaultAppliedRef.current = true;
  }, [chatbot, chatbotId]);

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
      canAddChatbot: true,
      isPreviewMode: false,
      portalLoading: false,
      showSubscriptionNav,
    });
    return () => setNav(null);
  }, [isValidId, setNav, router, showSubscriptionNav]);

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
      logClientIssue('chatbotPreview.send', error);
      const errorMsg = getUserFacingFetchError(error, 'Something went wrong. Please try again.');
      const errorMessage: MessageType = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: `Sorry, I encountered an error: ${errorMsg}`,
        timestamp: Date.now(),
      };
      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  /** Enter sends only when not waiting for the assistant (input stays editable while thinking). */
  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      if (!isLoading) handleSendMessage();
    }
  };

  const hasJesusFeature = chatbot?.jesusTeachingsEnabled || chatbot?.bibleVerse;
  const selectedScreenWidth = SCREEN_WIDTHS[screenPreview];
  const isMobilePreview = screenPreview === 'mobile';

  /**
   * Desktop/tablet: bottom-right panel, width ~embed. Height scales with the preview frame (like mobile’s
   * min(50dvh, 100%-chrome)) so it does not tower over the mock browser; capped ~embed max (500px).
   */
  const desktopTabletEmbedStyle = useMemo(() => {
    const borderRadius = parseInt(theme.borderRadius, 10) > 0 ? parseInt(theme.borderRadius, 10) : 12;
    if (screenPreview === 'tablet') {
      return {
        width: 308,
        height: 'min(480px, max(260px, calc(0.34 * (100% - 62px))))',
        maxHeight: 'calc(100% - 62px)',
        top: 'auto',
        right: 14,
        bottom: 14,
        borderRadius,
      } as const;
    }
    return {
      width: 332,
      height: 'min(500px, max(272px, calc(0.36 * (100% - 68px))))',
      maxHeight: 'calc(100% - 66px)',
      top: 'auto',
      right: 18,
      bottom: 18,
      borderRadius,
    } as const;
  }, [screenPreview, theme.borderRadius]);

  useLayoutEffect(() => {
    const el = previewDeviceFrameRef.current;
    if (!el) return;
    const ro = new ResizeObserver(() => {
      setPreviewFrameHeight(el.clientHeight);
    });
    ro.observe(el);
    setPreviewFrameHeight(el.clientHeight);
    return () => ro.disconnect();
  }, [analysisLoading, chatbotId, sceneMode, screenPreview]);

  useEffect(() => {
    if (chatbotId === null) {
      setPreviewWidgetHeightPx(null);
      return;
    }
    try {
      const raw = localStorage.getItem(`prayer-chat-preview-h-${chatbotId}`);
      if (!raw?.trim()) {
        setPreviewWidgetHeightPx(null);
        return;
      }
      const n = parseInt(raw, 10);
      if (Number.isFinite(n)) setPreviewWidgetHeightPx(n);
      else setPreviewWidgetHeightPx(null);
    } catch {
      setPreviewWidgetHeightPx(null);
    }
  }, [chatbotId]);

  useEffect(() => {
    if (previewWidgetHeightPx === null || previewFrameHeight < 80) return;
    const maxH = Math.max(220, previewFrameHeight - 48);
    if (previewWidgetHeightPx > maxH) setPreviewWidgetHeightPx(maxH);
  }, [previewFrameHeight, previewWidgetHeightPx]);

  /** Min/max panel height (px) inside the preview device frame — single source for drag, clamp, and ARIA */
  const previewWidgetHeightLimits = useMemo(() => {
    const frameH = previewFrameHeight > 0 ? previewFrameHeight : 640;
    const maxH = Math.max(220, frameH - 48);
    return { min: 220, max: maxH };
  }, [previewFrameHeight]);

  const widgetGripTopBorderRadius = useMemo(() => {
    const px = parseInt(theme.borderRadius, 10);
    return Number.isFinite(px) && px > 0 ? theme.borderRadius : '12px';
  }, [theme.borderRadius]);

  useEffect(() => {
    return () => {
      previewResizeDragRef.current = null;
    };
  }, []);

  const mobilePreviewWidgetBaseStyle = useMemo(
    () =>
      ({
        left: 10,
        right: 10,
        top: 'auto',
        bottom: 'max(10px, env(safe-area-inset-bottom))',
        width: 'auto',
        height: 'min(calc(100% - 56px), max(260px, 55%))',
        maxHeight: 'calc(100% - 56px)',
        minHeight: 260,
        borderRadius: 16,
        boxSizing: 'border-box' as const,
      }) as const,
    []
  );

  const previewWidgetPanelStyle = useMemo(() => {
    const { min, max } = previewWidgetHeightLimits;
    if (previewWidgetHeightPx === null) {
      return isMobilePreview ? mobilePreviewWidgetBaseStyle : desktopTabletEmbedStyle;
    }
    const h = Math.min(Math.max(min, previewWidgetHeightPx), max);
    if (isMobilePreview) {
      return {
        ...mobilePreviewWidgetBaseStyle,
        height: h,
        maxHeight: max,
        minHeight: Math.min(260, h),
      };
    }
    return {
      ...desktopTabletEmbedStyle,
      height: h,
      maxHeight: max,
    };
  }, [
    isMobilePreview,
    mobilePreviewWidgetBaseStyle,
    desktopTabletEmbedStyle,
    previewWidgetHeightPx,
    previewWidgetHeightLimits,
  ]);

  const onPreviewWidgetResizePointerDown = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    e.preventDefault();
    const panel = previewWidgetPanelRef.current;
    if (!panel) return;
    e.currentTarget.setPointerCapture(e.pointerId);
    previewResizeDragRef.current = {
      startY: e.clientY,
      startH: panel.getBoundingClientRect().height,
      pointerId: e.pointerId,
    };
  }, []);

  const onPreviewWidgetResizePointerMove = useCallback((e: React.PointerEvent<HTMLDivElement>) => {
    const d = previewResizeDragRef.current;
    if (!d || e.pointerId !== d.pointerId) return;
    const frame = previewDeviceFrameRef.current;
    if (!frame) return;
    const min = 220;
    const max = Math.max(min, frame.clientHeight - 48);
    const dy = e.clientY - d.startY;
    const next = Math.round(d.startH - dy);
    setPreviewWidgetHeightPx(Math.min(max, Math.max(min, next)));
  }, []);

  const onPreviewWidgetResizePointerUp = useCallback(
    (e: React.PointerEvent<HTMLDivElement>) => {
      const d = previewResizeDragRef.current;
      if (!d || e.pointerId !== d.pointerId) return;
      previewResizeDragRef.current = null;
      try {
        e.currentTarget.releasePointerCapture(e.pointerId);
      } catch {
        /* ignore */
      }
      if (chatbotId === null) return;
      const h = previewWidgetPanelRef.current?.getBoundingClientRect().height;
      if (h && Number.isFinite(h)) {
        try {
          localStorage.setItem(`prayer-chat-preview-h-${chatbotId}`, String(Math.round(h)));
        } catch {
          /* ignore */
        }
      }
    },
    [chatbotId]
  );

  const onPreviewWidgetResizeKeyDown = useCallback(
    (e: React.KeyboardEvent<HTMLDivElement>) => {
      const key = e.key;
      if (key !== 'ArrowUp' && key !== 'ArrowDown' && key !== 'Home' && key !== 'End') return;
      const panel = previewWidgetPanelRef.current;
      const frame = previewDeviceFrameRef.current;
      if (!panel || !frame) return;
      e.preventDefault();
      const min = previewWidgetHeightLimits.min;
      const max = Math.max(min, frame.clientHeight - 48);
      const currentPx =
        previewWidgetHeightPx !== null
          ? previewWidgetHeightPx
          : Math.round(panel.getBoundingClientRect().height);
      let next = currentPx;
      if (key === 'ArrowUp') next = currentPx + 24;
      else if (key === 'ArrowDown') next = currentPx - 24;
      else if (key === 'Home') next = max;
      else if (key === 'End') next = min;
      const clamped = Math.min(max, Math.max(min, Math.round(next)));
      setPreviewWidgetHeightPx(clamped);
      if (chatbotId !== null) {
        try {
          localStorage.setItem(`prayer-chat-preview-h-${chatbotId}`, String(clamped));
        } catch {
          /* ignore */
        }
      }
    },
    [previewWidgetHeightPx, previewWidgetHeightLimits.min, chatbotId]
  );

  const previewResizeAriaValueNow = useMemo(() => {
    if (previewWidgetHeightPx === null) return undefined;
    const { min, max } = previewWidgetHeightLimits;
    return Math.min(max, Math.max(min, previewWidgetHeightPx));
  }, [previewWidgetHeightPx, previewWidgetHeightLimits]);

  const websiteHost = getHostname(chatbot?.websiteUrl);
  const websitePreviewUrl = getSafeWebsitePreviewUrl(chatbot?.websiteUrl);

  useEffect(() => {
    if (!isValidId || chatbotId === null) {
      setPreviewToolbarControls(null);
      return;
    }
    if (analysisLoading || !chatbot) {
      setPreviewToolbarControls(null);
      return;
    }
    setPreviewToolbarControls({
      theme: { primaryColor: theme.primaryColor },
      previewMode,
      setPreviewMode,
      sceneMode,
      setSceneMode,
      screenPreview,
      setScreenPreview,
      websitePreviewUrl,
    });
    return () => setPreviewToolbarControls(null);
  }, [
    isValidId,
    chatbotId,
    analysisLoading,
    chatbot,
    theme.primaryColor,
    previewMode,
    sceneMode,
    screenPreview,
    websitePreviewUrl,
    setPreviewToolbarControls,
  ]);

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
      className="flex min-h-[100dvh] flex-1 flex-col overflow-x-hidden overflow-y-auto"
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

      <JesusGuidanceCard
        chatbot={chatbot}
        hasJesusFeature={Boolean(hasJesusFeature)}
        jesusPreview={jesusPreview}
        jesusPreviewLoading={jesusPreviewLoading}
        jesusPreviewError={jesusPreviewError}
      />

      {/* Preview layout controls live in the sticky app header (Layout button). */}

      {/* Preview canvas: taller min-heights on phones so the device frame fills more of the screen */}
      <div className="flex min-h-[min(80dvh,680px)] w-full max-w-[100vw] flex-1 flex-col px-1.5 py-2 sm:min-h-[72dvh] sm:px-2 md:min-h-[min(88dvh,920px)] md:p-3">
        <div
          ref={previewScrollRef}
          className={`flex min-h-[min(78dvh,640px)] w-full min-w-0 flex-1 flex-col sm:min-h-[70dvh] md:min-h-[min(86dvh,880px)] ${
            previewMode === 'actual' ? 'touch-pan-x overflow-x-auto overscroll-x-contain' : 'overflow-x-hidden'
          }`}
        >
          <div
            className="mx-auto flex min-h-[min(76dvh,600px)] min-w-0 flex-1 flex-col transition-all duration-200 sm:min-h-[68dvh] md:min-h-[min(84dvh,860px)]"
            style={
              previewMode === 'actual'
                ? { width: `${selectedScreenWidth}px`, minWidth: `${selectedScreenWidth}px` }
                : { width: '100%', maxWidth: '100%', minWidth: 0 }
            }
          >
            <div
              ref={previewDeviceFrameRef}
              data-testid="preview-device-frame"
              className="relative isolate flex min-h-[min(76dvh,600px)] w-full min-w-0 flex-1 flex-col overflow-hidden rounded-2xl border border-brown-200/80 bg-gradient-to-br from-white via-brown-50/30 to-amber-50/40 sm:min-h-[68dvh] md:min-h-[min(82dvh,840px)]"
            >
              {sceneMode === 'website' && websitePreviewUrl && (
                <iframe
                  src={websitePreviewUrl}
                  title="Website preview (static — scripts disabled so your live embed does not load here)"
                  className="absolute inset-0 z-0 w-full h-full border-0"
                  // No allow-scripts: customer sites that include our embed would otherwise run a second widget under the dashboard preview.
                  sandbox="allow-forms allow-popups"
                  referrerPolicy="no-referrer"
                  loading="lazy"
                  onLoad={() => setWebsiteFrameLoaded(true)}
                />
              )}
              {sceneMode === 'website' && websiteFrameLikelyBlocked && !websiteFrameLoaded && (
                <div className="absolute inset-0 z-[5] bg-white/95">
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

                    <details className="group mt-1 rounded-lg border border-brown-200/70 bg-white/60 text-left [touch-action:manipulation]">
                      <summary className="flex min-h-10 sm:min-h-0 cursor-pointer list-none items-center justify-between gap-2 px-2.5 sm:px-2 py-2.5 sm:py-1.5 text-xs sm:text-[11px] font-medium text-brown-700 select-none [&::-webkit-details-marker]:hidden">
                        <span className="min-w-0 flex-1 text-pretty leading-snug">Why this mock site?</span>
                        <ChevronDown className="h-4 w-4 sm:h-3.5 sm:w-3.5 shrink-0 text-brown-500 transition-transform duration-200 group-open:rotate-180" aria-hidden />
                      </summary>
                      <p className="border-t border-brown-100 px-2.5 sm:px-2 py-2.5 sm:py-2 text-xs sm:text-[11px] leading-snug text-brown-600 text-pretty">
                        The real site did not load in the frame (often due to security headers). This placeholder lets you
                        preview widget placement and theme anyway.
                      </p>
                    </details>
                  </div>
                </div>
              )}
              {sceneMode === 'website' && (
                <div className="absolute inset-0 z-10 bg-white/40 pointer-events-none" aria-hidden />
              )}
              <div className="absolute inset-0 z-[11] pointer-events-none">
                <div className="w-full h-12 border-b border-brown-100/80 bg-white/60" />
              </div>
              {sceneMode === 'website' && websiteFrameLikelyBlocked && !websiteFrameLoaded && (
                <div className="absolute top-2 left-1/2 z-[15] max-w-[min(100%-1rem,20rem)] -translate-x-1/2 px-3 py-1.5 text-center text-pretty rounded-full bg-amber-100 border border-amber-300 text-amber-800 text-[11px] sm:text-xs leading-snug">
                  Website blocked iframe preview. Showing widget only.
                </div>
              )}

              {isWidgetOpen && (
                <div
                  ref={previewWidgetPanelRef}
                  data-testid="preview-widget-panel"
                  className="absolute z-20 shadow-2xl border border-brown-200/80 bg-white/95 backdrop-blur-sm overflow-hidden"
                  style={previewWidgetPanelStyle}
                >
                  <div className="h-full flex flex-col overflow-hidden">
                    <div
                      role={previewWidgetHeightPx !== null ? 'slider' : undefined}
                      tabIndex={0}
                      aria-orientation={previewWidgetHeightPx !== null ? 'vertical' : undefined}
                      aria-label="Chat height. Drag, or use arrow keys to resize."
                      aria-valuemin={previewWidgetHeightPx !== null ? previewWidgetHeightLimits.min : undefined}
                      aria-valuemax={previewWidgetHeightPx !== null ? previewWidgetHeightLimits.max : undefined}
                      aria-valuenow={previewResizeAriaValueNow}
                      title="Drag to resize height"
                      className="preview-widget-resize-grip flex shrink-0 cursor-ns-resize select-none items-center justify-center border-b border-brown-200/70 bg-brown-100/90 py-1 touch-none focus:outline-none focus-visible:ring-2 focus-visible:ring-brown-400/80 focus-visible:ring-offset-1"
                      style={{
                        borderRadius: `${widgetGripTopBorderRadius} ${widgetGripTopBorderRadius} 0 0`,
                      }}
                      onPointerDown={onPreviewWidgetResizePointerDown}
                      onPointerMove={onPreviewWidgetResizePointerMove}
                      onPointerUp={onPreviewWidgetResizePointerUp}
                      onPointerCancel={onPreviewWidgetResizePointerUp}
                      onKeyDown={onPreviewWidgetResizeKeyDown}
                    >
                      <GripHorizontal className="h-4 w-4 text-brown-500/80" strokeWidth={2} aria-hidden />
                    </div>
                    <div className="flex items-center justify-between gap-2 px-3 py-2.5 sm:px-4 sm:py-3 text-white shrink-0" style={{ backgroundColor: theme.primaryColor }}>
                      <div className="min-w-0 flex-1 text-left text-sm font-semibold leading-snug text-pretty line-clamp-2 sm:text-base sm:leading-normal md:line-clamp-none md:truncate">
                        {chatbot?.name ?? 'AI Assistant'}
                      </div>
                      {/* Same behavior as embed #prayer-chat-close-btn → collapse to launcher; chevron reads as “minimize” */}
                      <button
                        type="button"
                        onClick={() => setIsWidgetOpen(false)}
                        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-white/90 transition-colors hover:bg-white/15 hover:text-white [touch-action:manipulation]"
                        aria-label="Minimize chat"
                      >
                        <ChevronDown className="h-5 w-5" strokeWidth={2.5} aria-hidden />
                      </button>
                    </div>

                    <div
                      ref={messagesContainerRef}
                      className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden px-3 py-3 sm:px-[15px] sm:py-[15px] bg-gradient-to-b from-brown-50/40 to-gold-50/30 custom-scrollbar"
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
                      <div className="flex-shrink-0 px-3 sm:px-[15px] py-2 border-t border-brown-200/80 bg-brown-50/60">
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

                    <div className="flex-shrink-0 px-3 py-3 sm:px-[15px] sm:py-[15px] border-t border-brown-200/80 bg-white">
                      <div className="flex gap-2 sm:gap-[10px] min-w-0 items-center">
                        <input
                          type="text"
                          value={input}
                          onChange={(e) => setInput(e.target.value)}
                          onKeyDown={handleKeyPress}
                          placeholder="Type your message..."
                          className="min-w-0 flex-1 px-3 py-2 rounded-[20px] border focus:outline-none focus:ring-2 bg-white text-brown-900 placeholder:text-brown-400 text-sm"
                          style={{ borderColor: `${theme.secondaryColor}cc` }}
                        />
                        <button
                          type="button"
                          onClick={() => handleSendMessage()}
                          disabled={!input.trim() || isLoading}
                          className="flex-shrink-0 text-white rounded-full font-medium disabled:opacity-50 hover:shadow-lg transition-all w-10 h-10 min-w-[40px] min-h-[40px] flex items-center justify-center"
                          style={{ backgroundColor: theme.primaryColor }}
                          aria-label="Send message"
                          aria-busy={isLoading}
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
                  className="absolute z-20 flex items-center justify-center rounded-full text-white shadow-xl transition-transform hover:scale-105 [touch-action:manipulation]"
                  style={{
                    width: isMobilePreview ? 50 : 60,
                    height: isMobilePreview ? 50 : 60,
                    right: isMobilePreview ? 10 : 20,
                    bottom: isMobilePreview ? 'max(10px, env(safe-area-inset-bottom))' : 20,
                    backgroundColor: theme.primaryColor,
                  }}
                  aria-label="Open chat"
                >
                  <MessageCircle className={isMobilePreview ? 'h-6 w-6' : 'h-7 w-7'} strokeWidth={2} aria-hidden />
                </button>
              )}
            </div>
          </div>
        </div>
      </div>
    </main>
  );
}
