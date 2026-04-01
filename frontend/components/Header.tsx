'use client';

import { useState, useEffect, useRef } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Home, LayoutDashboard, User, CreditCard, Plus, X, LogOut, Menu, BookOpen } from 'lucide-react';
import { useDashboardNav } from '@/context/DashboardNavContext';

const NAV_LINK_BASE = 'inline-flex items-center justify-center gap-1.5 h-9 px-3 py-2 rounded-xl text-sm font-medium whitespace-nowrap min-h-[36px]';

export default function Header() {
  const pathname = usePathname();
  const isHomePage = pathname === '/';
  const isDashboardPage = pathname === '/dashboard';
  const isAccountPage = pathname === '/account';
  const isChatbotPreviewPage = pathname.startsWith('/chatbot/');
  const isPricingPage = pathname === '/pricing';
  const nav = useDashboardNav();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

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
      <div className="max-w-4xl mx-auto px-4 sm:px-6 md:px-8 py-3 flex items-center justify-between gap-3 min-h-[52px]">
        {/* Left: page title — no truncation; fixed height */}
        <div className="flex items-center gap-2 min-w-0 flex-shrink-0">
          {leftLabel ? (
            <>
              <leftLabel.icon className="w-5 h-5 text-brown-700 flex-shrink-0" aria-hidden />
              <span className="text-brown-700 font-medium text-sm whitespace-nowrap">
                {leftLabel.text}
              </span>
            </>
          ) : (
            <Link
              href="/"
              className={`${NAV_LINK_BASE} text-brown-700 hover:text-gold-700 transition-colors bg-transparent border-0`}
            >
              <Home className="w-5 h-5 flex-shrink-0" />
              <span>Back to Home</span>
            </Link>
          )}
        </div>

        {/* Pricing: single Dashboard button */}
        {isPricingPage && (
          <Link
            href="/dashboard"
            className={`${NAV_LINK_BASE} bg-white border border-brown-200 text-brown-800 hover:bg-brown-50 transition-colors`}
          >
            <LayoutDashboard className="w-4 h-4 flex-shrink-0" />
            <span>Dashboard</span>
          </Link>
        )}

        {/* App nav: hamburger + dropdown on all screen sizes (desktop and mobile) */}
        {showAppNav && (
          <div className="relative flex-shrink-0" ref={menuRef} aria-label="Main">
              <button
                type="button"
                onClick={() => setMobileMenuOpen((o) => !o)}
                className={`${NAV_LINK_BASE} bg-brown-100/70 border border-brown-200 text-brown-800 hover:bg-brown-100 transition-colors`}
                aria-label={mobileMenuOpen ? 'Close menu' : 'Open menu'}
                aria-expanded={mobileMenuOpen}
              >
                {mobileMenuOpen ? <X className="w-5 h-5" /> : <Menu className="w-5 h-5" />}
              </button>
              {mobileMenuOpen && (
                <div
                  className="absolute right-0 top-full mt-2 w-56 rounded-xl border border-brown-200 bg-white shadow-lg py-2 z-50"
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
                  {nav.hasChatbots && nav.isPreviewMode && (
                    <button
                      type="button"
                      onClick={() => {
                        nav.onDeleteAllChatbots();
                        setMobileMenuOpen(false);
                      }}
                      className="flex w-full items-center gap-2 px-4 py-3 text-red-600 hover:bg-red-50 text-sm font-medium min-h-[44px] text-left"
                      role="menuitem"
                    >
                      Delete All
                    </button>
                  )}
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
        )}

        {/* Dashboard loading: show Back to Home until nav is set */}
        {isDashboardPage && !nav && (
          <Link
            href="/"
            className={`${NAV_LINK_BASE} text-brown-700 hover:text-gold-700 bg-transparent border-0 md:flex`}
          >
            <Home className="w-4 h-4 flex-shrink-0" />
            <span>Back to Home</span>
          </Link>
        )}

        {/* Account / Preview loading: show Back to Home until nav is set */}
        {(isAccountPage || isChatbotPreviewPage) && !nav && (
          <Link
            href="/"
            className={`${NAV_LINK_BASE} text-brown-700 hover:text-gold-700 bg-transparent border-0 md:flex`}
          >
            <Home className="w-4 h-4 flex-shrink-0" />
            <span>Back to Home</span>
          </Link>
        )}
      </div>
    </header>
  );
}
