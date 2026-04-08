import Link from 'next/link';

export default function Footer() {
  return (
    <footer className="border-t bg-gradient-to-r from-brown-50 to-gold-50 mt-8 sm:mt-12 pb-[env(safe-area-inset-bottom)]">
      <div className="max-w-5xl mx-auto px-4 py-4 sm:py-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:justify-between sm:items-center text-sm sm:text-base text-brown-700 text-center sm:text-left">
          <div className="order-2 sm:order-1">
            © {new Date().getFullYear()}{' '}
            <a
              href="https://www.raimonvibe.eu/"
              target="_blank"
              rel="noopener noreferrer"
              className="hover:text-gold-700 transition-colors font-semibold"
            >
              RaimonVibe
            </a>
            . All rights reserved.
          </div>
          <nav className="flex flex-wrap justify-center gap-4 sm:gap-6 order-1 sm:order-2" aria-label="Footer links">
            <Link
              href="/contact"
              className="hover:text-gold-700 transition-colors py-2 min-h-[44px] min-w-[44px] inline-flex items-center justify-center"
            >
              Contact
            </Link>
            <Link
              href="/privacy"
              className="hover:text-gold-700 transition-colors py-2 min-h-[44px] min-w-[44px] inline-flex items-center justify-center"
            >
              Privacy Notice
            </Link>
            <Link
              href="/legal"
              className="hover:text-gold-700 transition-colors py-2 min-h-[44px] min-w-[44px] inline-flex items-center justify-center"
            >
              Legal Notice
            </Link>
            <Link
              href="/troubleshooting"
              className="hover:text-gold-700 transition-colors py-2 min-h-[44px] min-w-[44px] inline-flex items-center justify-center"
            >
              Troubleshooting
            </Link>
          </nav>
        </div>
      </div>
    </footer>
  );
}
