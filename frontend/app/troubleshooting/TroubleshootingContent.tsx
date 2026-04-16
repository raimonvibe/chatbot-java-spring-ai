'use client';

import Link from 'next/link';
import { useMemo, useState } from 'react';
import { AlertCircle, CheckCircle2, Wrench, Heart, ShieldCheck, Search } from 'lucide-react';

type Topic = {
  id: string;
  title: string;
  summary: string;
  steps: string[];
};

const TOPICS: Topic[] = [
  {
    id: 'new-chatbot-not-appearing',
    title: '1) New chatbot not appearing',
    summary:
      'Your old bot appears, but your new bot does not. This usually means your website is still using an old embed code or your app replaced the widget after load.',
    steps: [
      'Make sure you pasted the latest embed code from Dashboard.',
      "If you use React/Next.js, load the embed in a 'use client' component.",
      'Run embed initialization once in useEffect([]).',
    ],
  },
  {
    id: 'flash-then-disappear',
    title: '2) Chatbot flashes, then disappears',
    summary:
      'If the chatbot appears for a moment and then disappears, your app is likely re-rendering and removing the widget DOM.',
    steps: [
      'Keep embed injection in one mount-only useEffect([]).',
      'Do not re-inject on state changes or route changes.',
    ],
  },
  {
    id: 'input-style-broken',
    title: '3) Chat input text is light or hard to read',
    summary: "Your website's global CSS is likely overriding widget styles.",
    steps: [
      'Add a scoped CSS override in globals.css for [id^="prayer-chat-chatbot"].',
      'Reset only widget input styles so your site styles stay untouched.',
    ],
  },
  {
    id: 'nothing-appears',
    title: '4) Nothing appears at all',
    summary:
      'Common reasons: script in wrong place, stale cache, browser extension blocking scripts, HTTPS mismatch, or a strict security policy.',
    steps: [
      'Put script before </body>.',
      'Clear CDN/site cache and republish.',
      'Disable ad blockers/privacy extensions and test again.',
      'Use HTTPS everywhere (no HTTP assets).',
      'If using CSP/security headers, allow the chatbot script domain.',
    ],
  },
  {
    id: 'duplicate-widget',
    title: '5) Widget loads twice or behaves strangely',
    summary:
      'If the embed is added in multiple places, or initialized repeatedly, you may get duplicate widgets or odd behavior.',
    steps: ['Keep only one embed snippet.', 'Initialize the widget once.'],
  },
  {
    id: 'desktop-not-mobile',
    title: '6) Works on desktop, not on mobile',
    summary:
      'Mobile browsers can cache aggressively, and overlays can hide floating UI.',
    steps: [
      'Test in private mode on your phone.',
      'Check that small-screen CSS does not hide fixed elements.',
      'Check cookie banners/popups that may cover the widget button.',
    ],
  },
  {
    id: 'nextjs-react-setup',
    title: '7) Next.js / React setup from plain embed code',
    summary:
      'If you only have a plain HTML embed snippet, convert it into a client component and mount that component once in your app layout.',
    steps: [
      "Create a dedicated client component (example: src/components/PrayerChatWidget.tsx) and move script loading into useEffect([]).",
      'Set script src to your Prayer Chat backend URL + /js/chatbot-widget.js.',
      'Call PrayerChat.init({ embedCode, apiUrl }) in script.onload.',
      'Render one container div with id/data-embed-code that matches your embed code.',
      'Import that component in your Next.js layout and mount it once near the bottom of <body>.',
    ],
  },
];

export default function TroubleshootingContent() {
  const [query, setQuery] = useState('');
  const q = query.trim().toLowerCase();

  const filteredTopics = useMemo(() => {
    if (!q) return TOPICS;
    return TOPICS.filter((topic) => {
      const haystack = `${topic.title} ${topic.summary} ${topic.steps.join(' ')}`.toLowerCase();
      return haystack.includes(q);
    });
  }, [q]);

  return (
    <main className="min-h-screen px-4 py-8 sm:px-6 md:px-8">
      <div className="mx-auto w-full max-w-4xl">
        <header className="rounded-2xl border border-brown-200 bg-gradient-to-r from-brown-50 to-gold-50 p-6 sm:p-8 shadow-sm">
          <div className="mb-3 inline-flex items-center gap-2 rounded-full border border-brown-200 bg-white px-3 py-1 text-xs font-semibold text-brown-700">
            <Wrench className="h-4 w-4" />
            Setup Help
          </div>
          <h1 className="text-2xl font-bold tracking-tight text-brown-900 sm:text-3xl">Embed Troubleshooting Guide</h1>
          <p className="mt-3 text-sm leading-relaxed text-brown-700 sm:text-base">
            If your chatbot is not showing correctly, start here. This guide is beginner-friendly and step-by-step.
          </p>
          <p className="mt-2 text-sm leading-relaxed text-brown-700">
            <Heart className="mr-1 inline h-4 w-4 text-gold-700" />
            Take a breath, take one step at a time, and keep going with faith.
          </p>
        </header>

        <section className="mt-6 rounded-2xl border border-brown-200 bg-white p-5 sm:p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-brown-900">Search this page</h2>
          <div className="mt-3 relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-brown-500" />
            <input
              type="search"
              inputMode="search"
              autoComplete="off"
              spellCheck={false}
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder='Try keywords like "mobile", "CSS", "React", "cache", "HTTPS"'
              className="w-full rounded-xl border border-brown-300 bg-white py-2.5 pl-10 pr-3 text-sm text-brown-900 placeholder:text-brown-500 focus:border-gold-500 focus:outline-none focus:ring-2 focus:ring-gold-200"
              aria-label="Search troubleshooting topics"
            />
          </div>
          <p className="mt-2 text-xs text-brown-700">
            Showing {filteredTopics.length} of {TOPICS.length} topics.
          </p>
        </section>

        <section className="mt-6 rounded-2xl border border-brown-200 bg-white p-5 sm:p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-brown-900">Start here first (2-minute check)</h2>
          <p className="mt-2 text-sm text-brown-700">
            First choose your website type so you do not accidentally install the widget twice.
          </p>
          <div className="mt-3 grid gap-3 md:grid-cols-2">
            <article className="rounded-xl border border-brown-200 bg-brown-50 p-3">
              <p className="text-sm font-semibold text-brown-900">Plain HTML websites</p>
              <ol className="mt-2 list-decimal space-y-1 pl-5 text-sm text-brown-800">
                <li>Copy the newest embed code from your Dashboard.</li>
                <li>Paste it before the closing <code>&lt;/body&gt;</code> tag.</li>
                <li>Save and republish your site.</li>
              </ol>
            </article>
            <article className="rounded-xl border border-brown-200 bg-brown-50 p-3">
              <p className="text-sm font-semibold text-brown-900">Next.js / React websites</p>
              <ol className="mt-2 list-decimal space-y-1 pl-5 text-sm text-brown-800">
                <li>Do not paste the raw script directly in random components.</li>
                <li>Use one dedicated client component (guide in next section).</li>
                <li>Mount that component once in your app layout.</li>
              </ol>
            </article>
          </div>
          <ol className="mt-3 list-decimal space-y-2 pl-5 text-sm text-brown-800">
            <li>Hard refresh after publish (Ctrl+F5 on Windows, Cmd+Shift+R on Mac).</li>
            <li>Open your site in private/incognito mode and test again.</li>
          </ol>
          <p className="mt-3 text-xs text-brown-700">If it still does not work, use the sections below.</p>
        </section>

        <section className="mt-6 rounded-2xl border border-brown-200 bg-white p-5 sm:p-6 shadow-sm">
          <h2 className="text-lg font-semibold text-brown-900">Beginner guide: convert embed script for Next.js / React</h2>
          <p className="mt-2 text-sm leading-relaxed text-brown-700">
            Some sites use the chatbot embed directly in plain HTML. In Next.js and React, it is usually safer to place
            this logic in a dedicated client component and import it into your layout once.
          </p>
          <p className="mt-2 text-xs leading-relaxed text-brown-700">
            Quick glossary: <code>'use client'</code> means the file runs in the browser, <code>useEffect([])</code>{' '}
            means run once when the component loads, and <code>layout.tsx</code> is the shared wrapper for your pages.
          </p>

          <h3 className="mt-4 text-sm font-semibold text-brown-900">1) Create a widget component file</h3>
          <p className="mt-1 text-sm text-brown-700">
            Create <code>src/components/PrayerChatWidget.tsx</code> (or your own name). Use this format:
          </p>
          <pre className="mt-2 overflow-x-auto rounded-xl border border-brown-200 bg-brown-50 p-3 text-xs text-brown-900">
{`'use client'
import { useEffect } from 'react'

export default function PrayerChatWidget() {
  useEffect(() => {
    const embedCode = 'your-embed-code'
    const baseUrl = 'https://your-backend-domain.com'

    const script = document.createElement('script')
    script.src = baseUrl + '/js/chatbot-widget.js'
    script.async = true
    script.onerror = () => {
      const el = document.getElementById('prayer-chat-chatbot-' + embedCode)
      if (el) {
        el.innerHTML =
          '<p style="padding:12px;background:#fff3cd;border:1px solid #ffc107;border-radius:8px;font-family:sans-serif;font-size:14px;">Chat could not load. Check browser console (F12).</p>'
      }
    }
    script.onload = () => {
      if (typeof (window as any).PrayerChat !== 'undefined') {
        ;(window as any).PrayerChat.init({
          embedCode,
          apiUrl: baseUrl + '/api',
        })
      } else {
        const el = document.getElementById('prayer-chat-chatbot-' + embedCode)
        if (el) {
          el.innerHTML =
            '<p style="padding:12px;background:#f8d7da;border:1px solid #f5c6cb;border-radius:8px;font-family:sans-serif;font-size:14px;">Chat failed to start. Open console (F12) for details.</p>'
        }
      }
    }
    document.head.appendChild(script)
  }, [])

  return (
    <div
      id={'prayer-chat-chatbot-' + 'your-embed-code'}
      data-embed-code="your-embed-code"
      suppressHydrationWarning={true}
    />
  )
}`}
          </pre>

          <h3 className="mt-4 text-sm font-semibold text-brown-900">2) Import it in your layout</h3>
          <p className="mt-1 text-sm text-brown-700">
            In Next.js, import the component in <code>src/app/layout.tsx</code> and render it once near the bottom of{' '}
            <code>&lt;body&gt;</code>, for example right before closing <code>&lt;/body&gt;</code>.
          </p>

          <h3 className="mt-4 text-sm font-semibold text-brown-900">3) Verify values carefully</h3>
          <ul className="mt-1 space-y-1 text-sm text-brown-800">
            <li>- Use the exact embed code from your Prayer Chat dashboard.</li>
            <li>- Use your real backend URL for <code>baseUrl</code> (HTTPS).</li>
            <li>- Keep only one mounted widget component in your app.</li>
          </ul>

          <h3 className="mt-4 text-sm font-semibold text-brown-900">Optional: ask AI to convert it for you</h3>
          <p className="mt-1 text-sm text-brown-700">
            If you are not comfortable coding this by hand, paste your plain embed script into your AI assistant and ask
            it to convert it into a Next.js/React client component.
          </p>
          <pre className="mt-2 overflow-x-auto rounded-xl border border-brown-200 bg-brown-50 p-3 text-xs text-brown-900">
{`Convert this Prayer Chat embed snippet into:
1) a Next.js client component file: src/components/PrayerChatWidget.tsx
2) the import + usage snippet for src/app/layout.tsx

Requirements:
- use useEffect([]) so init runs once
- load script from baseUrl + '/js/chatbot-widget.js'
- call PrayerChat.init({ embedCode, apiUrl: baseUrl + '/api' })
- include script onerror/onload fallback text
- keep the code beginner-friendly and fully copy/paste ready.`}
          </pre>
          <p className="mt-2 text-xs text-brown-700">
            Always double-check the generated code, especially your <code>embedCode</code> and backend URL.
          </p>
        </section>

        <section className="mt-6 space-y-4">
          {filteredTopics.length === 0 ? (
            <article className="rounded-2xl border border-brown-200 bg-white p-5 sm:p-6 shadow-sm">
              <h2 className="text-lg font-semibold text-brown-900">No matching topic found</h2>
              <p className="mt-2 text-sm text-brown-700">
                Try a broader keyword like <strong>mobile</strong>, <strong>cache</strong>, or <strong>CSS</strong>.
              </p>
            </article>
          ) : (
            filteredTopics.map((topic) => (
              <article key={topic.id} className="rounded-2xl border border-brown-200 bg-white p-5 sm:p-6 shadow-sm">
                <h2 className="flex items-center gap-2 text-lg font-semibold text-brown-900">
                  <AlertCircle className="h-5 w-5 text-red-700" />
                  {topic.title}
                </h2>
                <p className="mt-3 break-words text-sm leading-relaxed text-brown-700">{topic.summary}</p>
                <div className="mt-3 break-words rounded-xl border border-green-200 bg-green-50 px-4 py-3 text-sm text-green-900">
                  <p className="font-semibold">What to do</p>
                  <ul className="mt-1 list-disc space-y-1 pl-5">
                    {topic.steps.map((step) => (
                      <li key={step}>{step}</li>
                    ))}
                  </ul>
                </div>
              </article>
            ))
          )}
        </section>

        <section className="mt-6 rounded-2xl border border-brown-200 bg-brown-50/90 p-5 sm:p-6">
          <h2 className="text-lg font-semibold text-brown-900">Quick checklist</h2>
          <ul className="mt-3 space-y-2 text-sm text-brown-800">
            <li className="flex items-start gap-2">
              <CheckCircle2 className="mt-0.5 h-4 w-4 text-green-700" />
              I copied the newest embed code from Dashboard.
            </li>
            <li className="flex items-start gap-2">
              <CheckCircle2 className="mt-0.5 h-4 w-4 text-green-700" />I pasted the script before <code>&lt;/body&gt;</code>.
            </li>
            <li className="flex items-start gap-2">
              <CheckCircle2 className="mt-0.5 h-4 w-4 text-green-700" />
              I only load the widget once (no duplicate script).
            </li>
            <li className="flex items-start gap-2">
              <CheckCircle2 className="mt-0.5 h-4 w-4 text-green-700" />
              I scoped CSS so global styles do not break widget input.
            </li>
            <li className="flex items-start gap-2">
              <CheckCircle2 className="mt-0.5 h-4 w-4 text-green-700" />
              I tested in private mode with extensions disabled.
            </li>
          </ul>
        </section>

        <section className="mt-6 rounded-2xl border border-emerald-200 bg-emerald-50 p-5 sm:p-6">
          <h2 className="flex items-center gap-2 text-lg font-semibold text-emerald-900">
            <ShieldCheck className="h-5 w-5" />
            When to contact support
          </h2>
          <p className="mt-2 text-sm leading-relaxed text-emerald-900">
            If you followed this guide and still have issues, send us:
          </p>
          <ul className="mt-2 space-y-1 text-sm text-emerald-900">
            <li>Your website URL</li>
            <li>Your chatbot name</li>
            <li>What you expected vs what happened</li>
            <li>A screenshot (desktop or mobile)</li>
          </ul>
          <p className="mt-3 text-xs text-emerald-900">We will do our best to help quickly and kindly.</p>
        </section>

        <div className="mt-8 flex flex-col gap-3 sm:flex-row">
          <Link
            href="/dashboard"
            className="inline-flex w-full items-center justify-center rounded-xl bg-gradient-to-r from-brown-600 to-gold-600 px-5 py-3 text-sm font-semibold text-white hover:shadow-lg sm:w-auto"
          >
            Back to Dashboard
          </Link>
          <Link
            href="/contact"
            className="inline-flex w-full items-center justify-center rounded-xl border border-brown-300 bg-white px-5 py-3 text-sm font-semibold text-brown-800 hover:bg-brown-50 sm:w-auto"
          >
            Need more help? Contact support
          </Link>
        </div>
      </div>
    </main>
  );
}
