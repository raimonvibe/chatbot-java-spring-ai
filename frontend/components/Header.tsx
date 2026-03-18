'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { Home } from 'lucide-react';

export default function Header() {
  const pathname = usePathname();
  const isHomePage = pathname === '/';

  // Don't show header on home page
  if (isHomePage) {
    return null;
  }

  return (
    <header className="bg-gradient-to-r from-brown-50 to-gold-50 border-b border-brown-200 sticky top-0 z-50">
      <div className="max-w-4xl mx-auto px-4 sm:px-6 md:px-8 py-3 flex items-center">
        <Link
          href="/"
          className="inline-flex items-center gap-2 text-brown-700 hover:text-gold-700 transition-colors font-medium text-sm"
        >
          <Home className="w-5 h-5 flex-shrink-0" />
          <span>Back to Home</span>
        </Link>
      </div>
    </header>
  );
}
