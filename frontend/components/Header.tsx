'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Home, LayoutDashboard } from 'lucide-react';

export default function Header() {
  const pathname = usePathname();
  const isHomePage = pathname === '/';
  const isDashboardPage = pathname === '/dashboard';
  const isPricingPage = pathname === '/pricing';

  // Don't show header on home page
  if (isHomePage) {
    return null;
  }

  return (
    <header className="bg-gradient-to-r from-brown-50 to-gold-50 border-b border-brown-200 sticky top-0 z-50">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 md:px-8 py-3 flex items-center justify-between gap-3">
        {/* Left: page title / back link */}
        <div className="flex items-center gap-2 min-w-0">
          {isDashboardPage ? (
            <>
              <LayoutDashboard className="w-5 h-5 text-brown-700 flex-shrink-0" />
              <span className="text-brown-700 hover:text-gold-700 transition-colors font-medium text-sm truncate hidden sm:inline">
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

        {/* Right: optional Dashboard button on Pricing */}
        {isPricingPage && (
          <Link
            href="/dashboard"
            className="inline-flex items-center gap-2 px-3 py-2 rounded-xl bg-white border border-brown-200 text-brown-800 hover:bg-brown-50 transition-colors text-sm font-medium h-9 whitespace-nowrap"
          >
            <LayoutDashboard className="w-4 h-4 flex-shrink-0" />
            <span>Dashboard</span>
          </Link>
        )}

        {/* Right: back-to-home link on Dashboard page for convenience */}
        {isDashboardPage && (
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
