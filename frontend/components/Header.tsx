'use client';

import { useState, useEffect, useRef } from 'react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Home, LayoutDashboard, User, CreditCard, Plus, X, LogOut, Menu } from 'lucide-react';
import { useDashboardNav } from '@/context/DashboardNavContext';

export default function Header() {
  const pathname = usePathname();
  const isHomePage = pathname === '/';
  const isDashboardPage = pathname === '/dashboard';
  const isPricingPage = pathname === '/pricing';
  const nav = useDashboardNav();
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

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

  // Don't show header on home page
  if (isHomePage) {
    return null;
  }

  const showDashboardNav = isDashboardPage && nav;

  return (
    <header className="bg-gradient-to-r from-brown-50 to-gold-50 border-b border-brown-200 sticky top-0 z-50">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 md:px-8 py-3 flex items-center justify-between gap-3">
        {/* Left: page title / back link */}
        <div className="flex items-center gap-2 min-w-0">
          {isDashboardPage ? (
            <>
              <LayoutDashboard className="w-5 h-5 text-brown-700 flex-shrink-0" />
              <span className="text-brown-700 font-medium text-sm truncate hidden sm:inline">
                Prayer-Chat Dashboard
              </span>
              <span className="text-brown-700 font-medium text-sm truncate inline sm:hidden">Dashboard</span>
            </>
          ) : (
            <Link
              href="/"
              className="inline-flex items-center gap-2 text-brown-700 hover:text-gold-700 transition-colors font-medium text-sm leading-none h-9"
            >
              <Home className="w-5 h-5 flex-shrink-0" />
              <span>Back to Home</span>
            </Link>
          )}
        </div>

        {/* Right: Pricing page - Dashboard button only */}
        {isPricingPage && (
          <Link
            href="/dashboard"
            className="inline-flex items-center gap-2 px-3 py-2 rounded-xl bg-white border border-brown-200 text-brown-800 hover:bg-brown-50 transition-colors text-sm font-medium h-9 whitespace-nowrap"
          >
            <LayoutDashboard className="w-4 h-4 flex-shrink-0" />
            <span>Dashboard</span>
          </Link>
        )}

        {/* Right: Dashboard nav - desktop inline */}
        {showDashboardNav && (
          <>
            <div className="hidden md:flex items-center gap-2 flex-wrap justify-end">
              <Link
                href="/dashboard"
                className="text-brown-700 hover:text-brown-900 transition-colors whitespace-nowrap py-1.5 px-1 text-sm font-medium"
              >
                Dashboard
              </Link>
              <Link
                href="/account"
                className="inline-flex items-center gap-1.5 whitespace-nowrap px-3 py-2 rounded-xl bg-brown-100/70 border border-brown-200 text-brown-800 hover:bg-brown-100 hover:text-brown-900 transition-colors text-sm font-medium"
                aria-label="Account"
              >
                <User className="w-4 h-4 flex-shrink-0" />
                <span>Account</span>
              </Link>
              <button
                onClick={nav.openSubscription}
                disabled={nav.portalLoading}
                className="inline-flex items-center gap-1.5 disabled:opacity-50 whitespace-nowrap px-3 py-2 rounded-xl bg-brown-100/70 border border-brown-200 text-brown-800 hover:bg-brown-100 hover:text-brown-900 transition-colors text-sm font-medium"
                aria-label="Subscription"
              >
                <CreditCard className="w-4 h-4 flex-shrink-0" />
                <span>{nav.portalLoading ? 'Opening…' : 'Subscription'}</span>
              </button>
              {nav.hasChatbots || nav.showCreateForm ? (
                <button
                  onClick={nav.toggleCreateForm}
                  className="inline-flex items-center gap-1.5 whitespace-nowrap px-3 py-2 rounded-lg bg-gradient-to-r from-brown-600 to-gold-600 text-white hover:from-brown-700 hover:to-gold-700 transition-all text-sm font-medium"
                  aria-label={nav.showCreateForm ? 'Cancel' : 'New Chatbot'}
                >
                  {nav.showCreateForm ? <X className="w-4 h-4" /> : <Plus className="w-4 h-4" />}
                  <span>{nav.showCreateForm ? 'Cancel' : 'New Chatbot'}</span>
                </button>
              ) : null}
              <button
                onClick={nav.logout}
                className="inline-flex items-center gap-1.5 whitespace-nowrap px-3 py-2 rounded-xl bg-brown-100/70 border border-brown-200 text-brown-800 hover:bg-brown-100 hover:text-brown-900 transition-colors text-sm font-medium"
                title="Log out"
                aria-label="Log out"
              >
                <LogOut className="w-4 h-4 flex-shrink-0" />
                <span>Logout</span>
              </button>
              {nav.hasChatbots && nav.isPreviewMode && (
                <button
                  onClick={nav.onDeleteAllChatbots}
                  className="text-red-600 hover:text-red-700 text-xs whitespace-nowrap py-1 font-medium"
                  title="Delete all chatbots (preview only)"
                >
                  Delete All
                </button>
              )}
              <Link
                href="/"
                className="inline-flex items-center gap-2 text-brown-700 hover:text-gold-700 transition-colors font-medium text-sm leading-none h-9 whitespace-nowrap ml-1"
              >
                <Home className="w-4 h-4 flex-shrink-0" />
                <span>Back to Home</span>
              </Link>
            </div>

            {/* Mobile: hamburger + dropdown */}
            <div className="md:hidden relative" ref={menuRef}>
              <button
                type="button"
                onClick={() => setMobileMenuOpen((o) => !o)}
                className="p-2 rounded-xl border border-brown-200 bg-brown-100/70 text-brown-800 hover:bg-brown-100 transition-colors"
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
                    className="flex items-center gap-2 px-4 py-3 text-brown-800 hover:bg-brown-50 text-sm font-medium"
                    role="menuitem"
                    onClick={() => setMobileMenuOpen(false)}
                  >
                    <LayoutDashboard className="w-4 h-4" />
                    Dashboard
                  </Link>
                  <Link
                    href="/account"
                    className="flex items-center gap-2 px-4 py-3 text-brown-800 hover:bg-brown-50 text-sm font-medium"
                    role="menuitem"
                    onClick={() => setMobileMenuOpen(false)}
                  >
                    <User className="w-4 h-4" />
                    Account
                  </Link>
                  <button
                    type="button"
                    onClick={() => {
                      nav.openSubscription();
                      setMobileMenuOpen(false);
                    }}
                    disabled={nav.portalLoading}
                    className="flex w-full items-center gap-2 px-4 py-3 text-brown-800 hover:bg-brown-50 text-sm font-medium disabled:opacity-50 text-left"
                    role="menuitem"
                  >
                    <CreditCard className="w-4 h-4" />
                    {nav.portalLoading ? 'Opening…' : 'Subscription'}
                  </button>
                  {(nav.hasChatbots || nav.showCreateForm) && (
                    <button
                      type="button"
                      onClick={() => {
                        nav.toggleCreateForm();
                        setMobileMenuOpen(false);
                      }}
                      className="flex w-full items-center gap-2 px-4 py-3 text-brown-800 hover:bg-brown-50 text-sm font-medium text-left"
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
                    className="flex w-full items-center gap-2 px-4 py-3 text-brown-800 hover:bg-brown-50 text-sm font-medium text-left"
                    role="menuitem"
                  >
                    <LogOut className="w-4 h-4" />
                    Logout
                  </button>
                  {nav.hasChatbots && nav.isPreviewMode && (
                    <button
                      type="button"
                      onClick={() => {
                        nav.onDeleteAllChatbots();
                        setMobileMenuOpen(false);
                      }}
                      className="flex w-full items-center gap-2 px-4 py-3 text-red-600 hover:bg-red-50 text-sm font-medium text-left"
                      role="menuitem"
                    >
                      Delete All
                    </button>
                  )}
                  <div className="border-t border-brown-100 my-2" />
                  <Link
                    href="/"
                    className="flex items-center gap-2 px-4 py-3 text-brown-800 hover:bg-brown-50 text-sm font-medium"
                    role="menuitem"
                    onClick={() => setMobileMenuOpen(false)}
                  >
                    <Home className="w-4 h-4" />
                    Back to Home
                  </Link>
                </div>
              )}
            </div>
          </>
        )}

        {/* Right: back-to-home only when on dashboard but nav not yet set (loading) */}
        {isDashboardPage && !nav && (
          <Link
            href="/"
            className="inline-flex items-center gap-2 text-brown-700 hover:text-gold-700 transition-colors font-medium text-sm leading-none h-9 whitespace-nowrap"
          >
            <Home className="w-4 h-4 flex-shrink-0" />
            <span className="hidden sm:inline">Back to Home</span>
          </Link>
        )}
      </div>
    </header>
  );
}
