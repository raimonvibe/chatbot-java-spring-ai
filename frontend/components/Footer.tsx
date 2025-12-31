import Link from 'next/link';

export default function Footer() {
  return (
    <footer className="border-t bg-gradient-to-r from-brown-50 to-gold-50 mt-12">
      <div className="max-w-5xl mx-auto px-4 py-6">
        <div className="flex flex-col sm:flex-row justify-between items-center gap-4 text-base text-brown-700">
          <div>
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
          <div className="flex flex-wrap justify-center sm:justify-end gap-4 sm:gap-6">
            <Link
              href="/contact"
              className="hover:text-gold-700 transition-colors"
            >
              Contact
            </Link>
            <Link
              href="/privacy"
              className="hover:text-gold-700 transition-colors"
            >
              Privacy Notice
            </Link>
            <Link
              href="/legal"
              className="hover:text-gold-700 transition-colors"
            >
              Legal Notice
            </Link>
          </div>
        </div>
      </div>
    </footer>
  );
}
