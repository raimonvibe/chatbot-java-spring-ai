'use client';

import { useState, useEffect, useRef } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import {
  Home,
  LayoutDashboard,
  User,
  CreditCard,
  Plus,
  X,
  LogOut,
  Menu,
  BookOpen,
  LayoutTemplate,
  Mail,
  Shield,
  Scale,
  Wrench,
} from 'lucide-react';
import { useDashboardNav } from '@/context/DashboardNavContext';
import { useChatbotPreviewControls } from '@/context/ChatbotPreviewControlsContext';
import PreviewLayoutPanel from '@/components/PreviewLayoutPanel';

/** Single height for all bar controls so the row does not shift between breakpoints or loading states. */
const NAV_LINK_BASE =
  'inline-flex items-center justify-center gap-1.5 h-10 shrink-0 px-3 rounded-xl text-sm font-medium leading-none whitespace-nowrap';

const PUBLIC_INFO_PATHS = ['/contact', '/privacy', '/legal', '/troubleshooting'] as const;

export default function Header() {
  const pathname = usePathname();
  const isHomePage = pathname === '/';
  const isDashboardPage = pathname === '/dashboard';
  const isAccountPage = pathname === '/account';
  const isChatbotPreviewPage = pathname.startsWith('/chatbot/');
  const isPricingPage = pathname === '/pricing';
  const isPublicInfoPage = (PUBLIC_INFO_PATHS as readonly string[]).includes(pathname);
  const nav = useDashboardNav();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const [previewLayoutOpen, setPreviewLayoutOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const previewLayoutRef = useRef<HTMLDivElement>(null);
  const previewControls = useChatbotPreviewControls();

  useEffect(() => {
    if (!previewControls) setPreviewLayoutOpen(false);
  }, [previewControls]);

  useEffect(() => {
    setMobileMenuOpen(false);
    setPreviewLayoutOpen(false);
  }, [pathname]);

  const showAppNav = (isDashboardPage || isAccountPage || isChatbotPreviewPage) && nav;

  useEffect(() => {
    if (!mobileMenuOpen) return;
    const close = (e: MouseEvent | KeyboardEvent) => {
      if (e instanceof KeyboardEvent && e.key !== 'Escape') return;
      const target = e instanceof MouseEvent ? e.target : null;
      if (target && menuRef.current && !menuRef.current.contains(target as Node)) {
        setMobileMenuOpen(false);
      } else if (e instanceof KeyboardEvent && e.key === 'Escape') {
        setMobileMenuOpen(false);
      }
    };
    document.addEventListener('click', close);
    document.addEventListener('keydown', close);
    return () => {
      document.removeEventListener('click', close);
      document.removeEventListener('keydown', close);
    };
  }, [mobileMenuOpen]);

  useEffect(() => {
    if (!previewLayoutOpen) return;
    const close = (e: MouseEvent | KeyboardEvent) => {
      if (e instanceof KeyboardEvent) {
        if (e.key === 'Escape') setPreviewLayoutOpen(false);
        return;
      }
      const target = e.target as Node | null;
      if (target && previewLayoutRef.current && !previewLayoutRef.current.contains(target)) {
        setPreviewLayoutOpen(false);
      }
    };
    document.addEventListener('click', close);
    document.addEventListener('keydown', close);
    return () => {
      document.removeEventListener('click', close);
      document.removeEventListener('keydown', close);
    };
  }, [previewLayoutOpen]);

  if (isHomePage) {
    return null;
  }

  const getLeftLabel = () => {
    if (isDashboardPage) return { icon: LayoutDashboard, text: 'Prayer-Chat Dashboard' };
    if (isAccountPage) return { icon: User, text: 'Account' };
    if (isChatbotPreviewPage) return { icon: BookOpen, text: 'Chatbot Preview' };
    return null;
  };

  const leftLabel = getLeftLabel();

  return (
    <header className="bg-gradient-to-r from-brown-50 to-gold-50 border-b border-brown-200 sticky top-0 z-50">
      <div className="mx-auto flex h-14 max-w-4xl items-center justify-between gap-2 px-3 sm:gap-3 sm:px-6 md:px-8">
        {/* Left: page title — truncates on narrow viewports so controls stay tappable */}
        <div className="flex min-h-0 min-w-0 flex-1 items-center gap-2 overflow-hidden">
          {leftLabel ? (
            <>
              <leftLabel.icon className="h-5 w-5 shrink-0 text-brown-700" aria-hidden />
              <span className="min-w-0 truncate text-sm font-medium leading-none text-brown-700">
                {leftLabel.text}
              </span>
            </>
          ) : (
            <Link
              href="/"
              className={`${NAV_LINK_BASE} text-brown-700 hover:text-gold-700 transition-colors bg-transparent border-0`}
            >
              <Home className="h-5 w-5 shrink-0" />
              <span>Back to Home</span>
            </Link>
          )}
        </div>

        {/* Pricing: single Dashboard button */}
        {isPricingPage && (
          <Link
            href="/dashboard"
            className={`${NAV_LINK_BASE} border border-brown-200 bg-white text-brown-800 transition-colors hover:bg-brown-50`}
          >
            <LayoutDashboard className="h-5 w-5 shrink-0" />
            <span>Dashboard</span>
          </Link>
        )}

        {/* Contact / legal / help: same footer links in a mobile-friendly menu */}
        {isPublicInfoPage && (
          <div className="relative flex h-10 shrink-0 items-center justify-end" ref={menuRef} aria-label="Site pages">
            <button
              type="button"
              onClick={() => setMobileMenuOpen((o) => !o)}
              className={`${NAV_LINK_BASE} border border-brown-200 bg-brown-100/70 text-brown-800 transition-colors hover:bg-brown-100`}
              aria-label={mobileMenuOpen ? 'Close menu' : 'Open menu'}
              aria-expanded={mobileMenuOpen}
            >
              {mobileMenuOpen ? <X className="h-5 w-5 shrink-0" /> : <Menu className="h-5 w-5 shrink-0" />}
            </button>
            {mobileMenuOpen && (
              <div
                className="z-50 rounded-xl border border-brown-200 bg-white py-2 shadow-lg max-sm:fixed max-sm:left-[max(0.75rem,env(safe-area-inset-left))] max-sm:right-[max(0.75rem,env(safe-area-inset-right))] max-sm:top-14 max-sm:mt-1 max-sm:w-auto max-sm:max-h-[min(70dvh,calc(100dvh-env(safe-area-inset-top)-env(safe-area-inset-bottom)-4rem))] max-sm:overflow-y-auto max-sm:overscroll-contain sm:absolute sm:right-0 sm:top-full sm:mt-2 sm:w-56 sm:max-h-none sm:overflow-visible"
                role="menu"
              >
                <Link
                  href="/contact"
                  className="flex items-center gap-2 px-4 py-3 text-brown-800 hover:bg-brown-50 text-sm font-medium min-h-[44px]"
                  role="menuitem"
                  aria-current={pathname === '/contact' ? 'page' : undefined}
                  onClick={() => setMobileMenuOpen(false)}
                >
                  <Mail className="h-4 w-4 shrink-0" aria-hidden />
                  Contact
                </Link>
                <Link
                  href="/privacy"
                  className="flex items-center gap-2 px-4 py-3 text-brown-800 hover:bg-brown-50 text-sm font-medium min-h-[44px]"
                  role="menuitem"
                  aria-current={pathname === '/privacy' ? 'page' : undefined}
                  onClick={() => setMobileMenuOpen(false)}
                >
                  <Shield className="h-4 w-4 shrink-0" aria-hidden />
                  Privacy Notice
                </Link>
                <Link
                  href="/legal"
                  className="flex items-center gap-2 px-4 py-3 text-brown-800 hover:bg-brown-50 text-sm font-medium min-h-[44px]"
                  role="menuitem"
                  aria-current={pathname === '/legal' ? 'page' : undefined}
                  onClick={() => setMobileMenuOpen(false)}
                >
                  <Scale className="h-4 w-4 shrink-0" aria-hidden />
                  Legal Notice
                </Link>
                <Link
                  href="/troubleshooting"
                  className="flex items-center gap-2 px-4 py-3 text-brown-800 hover:bg-brown-50 text-sm font-medium min-h-[44px]"
                  role="menuitem"
                  aria-current={pathname === '/troubleshooting' ? 'page' : undefined}
                  onClick={() => setMobileMenuOpen(false)}
                >
                  <Wrench className="h-4 w-4 shrink-0" aria-hidden />
                  Troubleshooting
                </Link>
                <div className="my-2 border-t border-brown-100" aria-hidden />
                <Link
                  href="/"
                  className="flex items-center gap-2 px-4 py-3 text-brown-800 hover:bg-brown-50 text-sm font-medium min-h-[44px]"
                  role="menuitem"
                  onClick={() => setMobileMenuOpen(false)}
                >
                  <Home className="h-4 w-4 shrink-0" aria-hidden />
                  Back to Home
                </Link>
              </div>
            )}
          </div>
        )}

        {/* App nav: chatbot preview layout (dropdown) + hamburger */}
        {showAppNav && (
          <div className="flex h-10 shrink-0 items-center justify-end gap-1.5 sm:gap-2">
            {isChatbotPreviewPage && previewControls && (
              <div className="relative" ref={previewLayoutRef}>
                <button
                  type="button"
                  data-testid="preview-layout-trigger"
                  onClick={() => {
                    setMobileMenuOpen(false);
                    setPreviewLayoutOpen((o) => !o);
                  }}
                  className={`${NAV_LINK_BASE} border border-brown-200 bg-white !px-2 text-brown-800 transition-colors hover:bg-brown-50 min-[380px]:!px-3`}
                  aria-expanded={previewLayoutOpen}
                  aria-haspopup="dialog"
                  aria-label="Preview layout — change size, background, and device frame"
                >
                  <LayoutTemplate className="h-5 w-5 shrink-0 text-brown-700" aria-hidden />
                  <span className="hidden min-[380px]:inline">Layout</span>
                </button>
                {previewLayoutOpen && (
                  <div
                    role="dialog"
                    aria-label="Preview layout options"
                    className="z-[60] rounded-xl border border-brown-200 bg-white p-3 shadow-xl max-sm:fixed max-sm:left-[max(0.75rem,env(safe-area-inset-left))] max-sm:right-[max(0.75rem,env(safe-area-inset-right))] max-sm:top-14 max-sm:mt-1 max-sm:max-h-[min(75dvh,calc(100dvh-env(safe-area-inset-top)-env(safe-area-inset-bottom)-4rem))] max-sm:w-auto max-sm:overflow-y-auto max-sm:overscroll-contain max-sm:pb-[max(0.75rem,env(safe-area-inset-bottom))] sm:absolute sm:right-0 sm:top-full sm:mt-2 sm:w-[min(calc(100vw-2rem),20rem)] sm:max-h-none sm:overflow-visible"
                  >
                    <p className="mb-2 text-xs font-semibold text-brown-800 sm:text-sm">How the embed preview looks</p>
                    <PreviewLayoutPanel api={previewControls} />
                  </div>
                )}
              </div>
            )}
            <div className="relative flex items-center justify-end" ref={menuRef} aria-label="Main">
              <button
                type="button"
                onClick={() => {
                  setPreviewLayoutOpen(false);
                  setMobileMenuOpen((o) => !o);
                }}
                className={`${NAV_LINK_BASE} border border-brown-200 bg-brown-100/70 text-brown-800 transition-colors hover:bg-brown-100`}
                aria-label={mobileMenuOpen ? 'Close menu' : 'Open menu'}
                aria-expanded={mobileMenuOpen}
              >
                {mobileMenuOpen ? <X className="h-5 w-5 shrink-0" /> : <Menu className="h-5 w-5 shrink-0" />}
              </button>
              {mobileMenuOpen && (
                <div
                  className="z-50 rounded-xl border border-brown-200 bg-white py-2 shadow-lg max-sm:fixed max-sm:left-[max(0.75rem,env(safe-area-inset-left))] max-sm:right-[max(0.75rem,env(safe-area-inset-right))] max-sm:top-14 max-sm:mt-1 max-sm:w-auto max-sm:max-h-[min(70dvh,calc(100dvh-env(safe-area-inset-top)-env(safe-area-inset-bottom)-4rem))] max-sm:overflow-y-auto max-sm:overscroll-contain sm:absolute sm:right-0 sm:top-full sm:mt-2 sm:w-56 sm:max-h-none sm:overflow-visible"
                  role="menu"
                >
                  <Link
                    href="/dashboard"
                    className="flex items-center gap-2 px-4 py-3 text-brown-800 hover:bg-brown-50 text-sm font-medium min-h-[44px]"
                    role="menuitem"
                    onClick={() => setMobileMenuOpen(false)}
                  >
                    <LayoutDashboard className="w-4 h-4 flex-shrink-0" />
                    Dashboard
                  </Link>
                  <Link
                    href="/account"
                    className="flex items-center gap-2 px-4 py-3 text-brown-800 hover:bg-brown-50 text-sm font-medium min-h-[44px]"
                    role="menuitem"
                    onClick={() => setMobileMenuOpen(false)}
                  >
                    <User className="w-4 h-4 flex-shrink-0" />
                    Account
                  </Link>
                  {nav.showSubscriptionNav && (
                    <button
                      type="button"
                      onClick={() => {
                        nav.openSubscription();
                        setMobileMenuOpen(false);
                      }}
                      disabled={nav.portalLoading}
                      className="flex w-full items-center gap-2 px-4 py-3 text-brown-800 hover:bg-brown-50 text-sm font-medium min-h-[44px] disabled:opacity-50 text-left"
                      role="menuitem"
                    >
                      <CreditCard className="w-4 h-4 flex-shrink-0" />
                      {nav.portalLoading ? 'Opening…' : 'Subscription'}
                    </button>
                  )}
                  {nav.canAddChatbot && (
                    <button
                      type="button"
                      onClick={() => {
                        nav.toggleCreateForm();
                        setMobileMenuOpen(false);
                      }}
                      className="flex w-full items-center gap-2 px-4 py-3 text-brown-800 hover:bg-brown-50 text-sm font-medium min-h-[44px] text-left"
                      role="menuitem"
                    >
                      {nav.showCreateForm ? <X className="w-4 h-4" /> : <Plus className="w-4 h-4" />}
                      {nav.showCreateForm ? 'Cancel' : 'New Chatbot'}
                    </button>
                  )}
                  <button
                    type="button"
                    onClick={() => {
                      nav.logout();
                      setMobileMenuOpen(false);
                    }}
                    className="flex w-full items-center gap-2 px-4 py-3 text-brown-800 hover:bg-brown-50 text-sm font-medium min-h-[44px] text-left"
                    role="menuitem"
                  >
                    <LogOut className="w-4 h-4 flex-shrink-0" />
                    Logout
                  </button>
                  <div className="border-t border-brown-100 my-2" aria-hidden />
                  <Link
                    href="/"
                    className="flex items-center gap-2 px-4 py-3 text-brown-800 hover:bg-brown-50 text-sm font-medium min-h-[44px]"
                    role="menuitem"
                    onClick={() => setMobileMenuOpen(false)}
                  >
                    <Home className="w-4 h-4 flex-shrink-0" />
                    Back to Home
                  </Link>
                </div>
              )}
            </div>
          </div>
        )}

        {/* Dashboard loading: same bar height as menu button to avoid vertical shift */}
        {isDashboardPage && !nav && (
          <div className="flex h-10 shrink-0 items-center justify-end">
            <Link
              href="/"
              className={`${NAV_LINK_BASE} border border-transparent bg-transparent text-brown-700 transition-colors hover:text-gold-700`}
            >
              <Home className="h-5 w-5 shrink-0" />
              <span>Back to Home</span>
            </Link>
          </div>
        )}

        {/* Account / Preview loading: match loaded-state right column geometry */}
        {(isAccountPage || isChatbotPreviewPage) && !nav && (
          <div className="flex h-10 shrink-0 items-center justify-end">
            <Link
              href="/"
              className={`${NAV_LINK_BASE} border border-transparent bg-transparent text-brown-700 transition-colors hover:text-gold-700`}
            >
              <Home className="h-5 w-5 shrink-0" />
              <span>Back to Home</span>
            </Link>
          </div>
        )}
      </div>
    </header>
  );
}
