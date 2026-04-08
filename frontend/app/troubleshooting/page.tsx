import type { Metadata } from 'next';
import Link from 'next/link';
import { AlertCircle, CheckCircle2, Wrench, Heart } from 'lucide-react';

export const metadata: Metadata = {
  title: 'Troubleshooting Embed Issues | Prayer-Chat',
  description:
    'Fix common Prayer-Chat embed issues like widget disappearing, hydration conflicts, and input styling overrides.',
};

export default function TroubleshootingPage() {
  return (
    <main className="min-h-screen px-4 py-8 sm:px-6 md:px-8">
      <div className="mx-auto w-full max-w-4xl">
        <header className="rounded-2xl border border-brown-200 bg-gradient-to-r from-brown-50 to-gold-50 p-6 sm:p-8 shadow-sm">
          <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-brown-200 bg-white px-3 py-1 text-xs font-semibold text-brown-700">
            <Wrench className="h-4 w-4" />
            Setup Help
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-brown-900 sm:text-3xl">
            Embed Troubleshooting Guide
          </h1>
          <p className="mt-3 text-sm leading-relaxed text-brown-700 sm:text-base">
            If your chatbot embed does not behave as expected, use this quick guide. We built it to be practical,
            calm, and easy to follow.
          </p>
          <p className="mt-2 text-sm leading-relaxed text-brown-700">
            <Heart className="mr-1 inline h-4 w-4 text-gold-700" />
            May this help you move forward with peace and confidence.
          </p>
        </header>

        <section className="mt-6 space-y-4">
          <article className="rounded-2xl border border-brown-200 bg-white p-5 sm:p-6 shadow-sm">
            <h2 className="flex items-center gap-2 text-lg font-semibold text-brown-900">
              <AlertCircle className="h-5 w-5 text-red-700" />
              1) New chatbot not appearing
            </h2>
            <p className="mt-3 text-sm leading-relaxed text-brown-700">
              Your new embed (for example, a new bot code) may fail to appear while an older one seemed fine.
              A common cause is a React hydration conflict: the widget injects DOM, then React hydration/re-render
              removes it.
            </p>
            <div className="mt-3 rounded-xl border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-900">
              <p className="font-semibold">Recommended fix</p>
              <p className="mt-1">
                Move embed initialization into a <code>{`'use client'`}</code> component and run it in{' '}
                <code>useEffect</code> with an empty dependency array (<code>[]</code>) so it runs once after mount.
              </p>
            </div>
          </article>

          <article className="rounded-2xl border border-brown-200 bg-white p-5 sm:p-6 shadow-sm">
            <h2 className="flex items-center gap-2 text-lg font-semibold text-brown-900">
              <AlertCircle className="h-5 w-5 text-red-700" />
              2) Chatbot flashes, then disappears
            </h2>
            <p className="mt-3 text-sm leading-relaxed text-brown-700">
              If the widget appears briefly and then vanishes, React likely re-rendered and replaced the DOM the
              widget inserted.
            </p>
            <div className="mt-3 rounded-xl border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-900">
              <p className="font-semibold">Recommended fix</p>
              <p className="mt-1">
                Keep embed injection inside one mount-only <code>useEffect([])</code> in a client component.
                Avoid re-running script injection on state changes.
              </p>
            </div>
          </article>

          <article className="rounded-2xl border border-brown-200 bg-white p-5 sm:p-6 shadow-sm">
            <h2 className="flex items-center gap-2 text-lg font-semibold text-brown-900">
              <AlertCircle className="h-5 w-5 text-red-700" />
              3) Chat input text is light or hard to read
            </h2>
            <p className="mt-3 text-sm leading-relaxed text-brown-700">
              Global CSS rules (for example, broad <code>input</code> color rules in <code>globals.css</code>)
              can bleed into the widget and override its styles.
            </p>
            <div className="mt-3 rounded-xl border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-900">
              <p className="font-semibold">Recommended fix</p>
              <p className="mt-1">
                Add a scoped override in <code>globals.css</code> for widget containers like{' '}
                <code>[id^="prayer-chat-chatbot"]</code> so styles are reset only inside the embed.
              </p>
            </div>
          </article>
        </section>

        <section className="mt-6 rounded-2xl border border-brown-200 bg-brown-50/90 p-5 sm:p-6">
          <h2 className="text-lg font-semibold text-brown-900">Quick checklist</h2>
          <ul className="mt-3 space-y-2 text-sm text-brown-800">
            <li className="flex items-start gap-2">
              <CheckCircle2 className="mt-0.5 h-4 w-4 text-green-700" />
              Embed script is loaded in a client-only mount effect.
            </li>
            <li className="flex items-start gap-2">
              <CheckCircle2 className="mt-0.5 h-4 w-4 text-green-700" />
              Script injection is not tied to changing React state.
            </li>
            <li className="flex items-start gap-2">
              <CheckCircle2 className="mt-0.5 h-4 w-4 text-green-700" />
              Widget input styles are protected from global CSS bleed.
            </li>
          </ul>
        </section>

        <div className="mt-8 flex flex-col gap-3 sm:flex-row">
          <Link
            href="/dashboard"
            className="inline-flex items-center justify-center rounded-xl bg-gradient-to-r from-brown-600 to-gold-600 px-5 py-3 text-sm font-semibold text-white hover:shadow-lg"
          >
            Back to Dashboard
          </Link>
          <Link
            href="/contact"
            className="inline-flex items-center justify-center rounded-xl border border-brown-300 bg-white px-5 py-3 text-sm font-semibold text-brown-800 hover:bg-brown-50"
          >
            Need more help? Contact support
          </Link>
        </div>
      </div>
    </main>
  );
}
