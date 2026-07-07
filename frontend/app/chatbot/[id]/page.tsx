'use client';

import { useState, useRef, useEffect, useLayoutEffect, useMemo, useCallback } from 'react';
import { motion } from 'framer-motion';
import { useParams, usePathname, useRouter } from 'next/navigation';
import {
  logout,
  AVATAR_IDS,
} from '@/lib/api';
import Link from 'next/link';
import { ChevronDown, GripHorizontal, MessageCircle } from 'lucide-react';
import ChatbotCreationLoader from '@/components/ChatbotCreationLoader';
import JesusGuidanceCard from '@/components/JesusGuidanceCard';
import EmbedChatWidget from '@/components/chat/EmbedChatWidget';
import { useSetDashboardNav } from '@/context/DashboardNavContext';
import { useChatbotPreviewControlsRegistration } from '@/context/ChatbotPreviewControlsContext';
import { useChatSession } from '@/hooks/useChatSession';
import { useChatbotPreview } from '@/hooks/useChatbotPreview';
import { useRequireAuth } from '@/hooks/useRequireAuth';
import { useSubscription } from '@/hooks/useSubscription';
import {
  parseBrandingConfig,
  parseChatbotId,
  getHostname,
  getSafeWebsitePreviewUrl,
  chatbotIdMatchesRoute,
} from '@/lib/chatbot-preview-utils';
import { isBillingEnabledFromEnv, paymentActionsAvailableFromApi } from '@/lib/billing-config';

export default function ChatbotPreview() {
  const params = useParams();
  const pathname = usePathname();
  const router = useRouter();
  const chatbotId = useMemo(() => parseChatbotId(params?.id), [params?.id]);
  const isValidId = chatbotId !== null;
  const { authenticated, loading: authLoading } = useRequireAuth();
  const setNav = useSetDashboardNav();
  const { setControls: setPreviewToolbarControls } = useChatbotPreviewControlsRegistration();
  const { apiData: subscriptionApi } = useSubscription(0);

  const {
    messages,
    resetMessages,
    input,
    setInput,
    isLoading,
    handleSendMessage,
    handleKeyDown,
  } = useChatSession({ chatbotId, enabled: isValidId && authenticated });

  const onChatbotLoaded = useCallback(
    (data: { name: string; description: string }) => {
      resetMessages([
        {
          id: '1',
          role: 'assistant',
          content: `Hello! I'm ${data.name}. ${data.description}`,
          timestamp: Date.now(),
        },
      ]);
    },
    [resetMessages]
  );

  const {
    chatbot,
    loadError,
    analysisLoading,
    quickReplies,
    jesusPreview,
    jesusPreviewLoading,
    jesusPreviewError,
  } = useChatbotPreview({
    chatbotId,
    pathname: pathname || '',
    enabled: isValidId && authenticated && !authLoading,
    onChatbotLoaded,
  });

  const [screenPreview, setScreenPreview] = useState<'desktop' | 'tablet' | 'mobile'>('desktop');
  const [previewMode, setPreviewMode] = useState<'fit' | 'actual'>('actual');
  const [sceneMode, setSceneMode] = useState<'plain' | 'website'>('plain');
  const [isWidgetOpen, setIsWidgetOpen] = useState(true);
  const [websiteFrameLoaded, setWebsiteFrameLoaded] = useState(false);
  const [websiteFrameLikelyBlocked, setWebsiteFrameLikelyBlocked] = useState(false);
  /** After hardening (safe URL only), default was plain; users expect website + widget on first open when URL exists. */
  const sceneDefaultAppliedRef = useRef(false);
  const [showSubscriptionNav, setShowSubscriptionNav] = useState(() => isBillingEnabledFromEnv());

  useEffect(() => {
    setShowSubscriptionNav(
      subscriptionApi ? paymentActionsAvailableFromApi(subscriptionApi) : isBillingEnabledFromEnv()
    );
  }, [subscriptionApi]);

  useEffect(() => {
    if (loadError !== 'unauthorized' || chatbotId === null) return;
    router.replace(`/login?redirect=${encodeURIComponent(pathname || `/chatbot/${chatbotId}`)}`);
  }, [loadError, chatbotId, pathname, router]);
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

  const messagesEndRef = useRef<HTMLDivElement>(null);

  /**
   * Narrow viewports: mobile frame + fit width so the mock phone and chat sheet fit the screen (no 390px overflow).
   */
  useLayoutEffect(() => {
    if (typeof window === 'undefined') return;
    if (!window.matchMedia('(max-width: 767px)').matches) return;
    setScreenPreview('mobile');
    setPreviewMode('fit');
  }, []);

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

  const hasJesusFeature = chatbot?.jesusTeachingsEnabled || chatbot?.bibleVerse;
  const selectedScreenWidth = SCREEN_WIDTHS[screenPreview];
  const isMobilePreview = screenPreview === 'mobile';

  /**
   * Desktop/tablet: bottom-right panel, width ~embed. Height scales with the preview frame (like mobile’s
   * min(50dvh, 100%-chrome)) so it does not tower over the mock browser; capped ~embed max (460px).
   */
  const desktopTabletEmbedStyle = useMemo(() => {
    const borderRadius = parseInt(theme.borderRadius, 10) > 0 ? parseInt(theme.borderRadius, 10) : 12;
    if (screenPreview === 'tablet') {
      return {
        width: 308,
        height: 'min(442px, max(260px, calc(0.34 * (100% - 62px))))',
        maxHeight: 'calc(100% - 62px)',
        top: 'auto',
        right: 14,
        bottom: 14,
        borderRadius,
      } as const;
    }
    return {
      width: 332,
      height: 'min(460px, max(272px, calc(0.36 * (100% - 68px))))',
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

  if (authLoading) {
    return (
      <main className="min-h-[100dvh] flex items-center justify-center p-6">
        <p className="text-brown-700">Loading…</p>
      </main>
    );
  }

  if (!authenticated) {
    return (
      <main className="min-h-[100dvh] flex items-center justify-center p-6">
        <p className="text-brown-700">Redirecting to login…</p>
      </main>
    );
  }

  if (loadError && loadError !== 'unauthorized') {
    return (
      <main className="min-h-[100dvh] flex items-center justify-center p-6">
        <div className="max-w-md text-center space-y-4">
          <p className="text-brown-800">{loadError}</p>
          <Link href="/dashboard" className="inline-block px-4 py-2 rounded-lg bg-brown-700 text-white">
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
                  title="Website preview (live page in sandboxed frame)"
                  className="absolute inset-0 z-0 w-full h-full border-0"
                  // allow-scripts + allow-same-origin: SPAs (e.g. Vercel) need JS and real origin to render. Tradeoff: sites that
                  // auto-inject our embed may show a second widget inside this iframe (dashboard overlay still shows your preview widget).
                  sandbox="allow-scripts allow-same-origin allow-forms allow-popups"
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
                  <EmbedChatWidget
                    chatbotName={chatbot?.name}
                    avatarId={chatbot?.avatarId}
                    theme={theme}
                    messages={messages}
                    quickReplies={quickReplies}
                    input={input}
                    isLoading={isLoading}
                    isMobilePreview={isMobilePreview}
                    messagesContainerRef={messagesContainerRef}
                    messagesEndRef={messagesEndRef}
                    onInputChange={setInput}
                    onSend={(text) => void handleSendMessage(text)}
                    onKeyDown={handleKeyDown}
                    headerRight={
                      <button
                        type="button"
                        onClick={() => setIsWidgetOpen(false)}
                        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg text-white/90 transition-colors hover:bg-white/15 hover:text-white [touch-action:manipulation]"
                        aria-label="Minimize chat"
                      >
                        <ChevronDown className="h-5 w-5" strokeWidth={2.5} aria-hidden />
                      </button>
                    }
                    topSlot={
                      <div
                        role={previewWidgetHeightPx !== null ? 'slider' : undefined}
                        tabIndex={0}
                        aria-orientation={previewWidgetHeightPx !== null ? 'vertical' : undefined}
                        aria-label="Chat height. Drag, or use arrow keys to resize."
                        aria-valuemin={previewWidgetHeightPx !== null ? previewWidgetHeightLimits.min : undefined}
                        aria-valuemax={previewWidgetHeightPx !== null ? previewWidgetHeightLimits.max : undefined}
                        aria-valuenow={previewResizeAriaValueNow}
                        title="Drag to resize height"
                        className={
                          `preview-widget-resize-grip flex shrink-0 cursor-ns-resize select-none items-center justify-center border-b border-brown-200/70 bg-brown-100/90 touch-none focus:outline-none focus-visible:ring-2 focus-visible:ring-brown-400/80 focus-visible:ring-offset-1 ${
                            isMobilePreview ? 'min-h-[52px] py-2.5' : 'min-h-[22px] py-0.5'
                          }`
                        }
                        style={{
                          borderRadius: `${widgetGripTopBorderRadius} ${widgetGripTopBorderRadius} 0 0`,
                        }}
                        onPointerDown={onPreviewWidgetResizePointerDown}
                        onPointerMove={onPreviewWidgetResizePointerMove}
                        onPointerUp={onPreviewWidgetResizePointerUp}
                        onPointerCancel={onPreviewWidgetResizePointerUp}
                        onKeyDown={onPreviewWidgetResizeKeyDown}
                      >
                        <GripHorizontal
                          className={`text-brown-500/80 ${isMobilePreview ? 'h-5 w-5' : 'h-3.5 w-3.5'}`}
                          strokeWidth={2}
                          aria-hidden
                        />
                      </div>
                    }
                  />
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
