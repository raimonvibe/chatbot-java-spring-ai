'use client';

import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { BookOpen, ChevronDown, ChevronUp } from 'lucide-react';
import type { Chatbot, JesusTeachingsPreviewResponse } from '@/lib/api';

interface JesusGuidanceCardProps {
  chatbot: Chatbot | null;
  hasJesusFeature: boolean;
  jesusPreview: JesusTeachingsPreviewResponse | null;
  jesusPreviewLoading: boolean;
  jesusPreviewError: string | null;
}

export default function JesusGuidanceCard({
  chatbot,
  hasJesusFeature,
  jesusPreview,
  jesusPreviewLoading,
  jesusPreviewError,
}: JesusGuidanceCardProps) {
  const [jesusCardOpen, setJesusCardOpen] = useState(false);
  const [jesusActiveTab, setJesusActiveTab] = useState<'verse' | 'teachings'>('verse');

  if (!hasJesusFeature) return null;

  return (
    <motion.div
      initial={false}
      className="flex-shrink-0 border-b border-brown-200/60 bg-gradient-to-r from-brown-50/80 to-amber-50/50 mb-1 md:mb-2"
    >
      <button
        type="button"
        onClick={() => setJesusCardOpen((o) => !o)}
        className="w-full flex items-center gap-3 px-4 py-2 text-left hover:bg-brown-100/50 transition-colors"
      >
        <div className="flex-shrink-0 w-9 h-9 rounded-lg bg-gradient-to-br from-brown-500 to-gold-600 flex items-center justify-center">
          <BookOpen className="w-5 h-5 text-white" strokeWidth={2} />
        </div>
        <span className="text-sm font-semibold text-brown-800">Jesus-inspired guidance</span>
        {jesusCardOpen ? (
          <ChevronUp className="w-4 h-4 text-brown-600 ml-auto" />
        ) : (
          <ChevronDown className="w-4 h-4 text-brown-600 ml-auto" />
        )}
      </button>
      <AnimatePresence>
        {jesusCardOpen && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2 }}
            className="overflow-hidden"
          >
            <div className="px-4 pb-3 pt-1 text-sm text-brown-700">
              <p className="mb-3 max-w-3xl text-xs md:text-sm">
                This chatbot weaves in gentle inspiration from Jesus&apos;s teachings and relevant Bible verses.
              </p>

              {/* Tabs */}
              <div className="flex flex-wrap gap-2 mb-3">
                <button
                  type="button"
                  onClick={() => setJesusActiveTab('verse')}
                  className={`px-3 py-1.5 text-xs rounded-full border ${
                    jesusActiveTab === 'verse'
                      ? 'bg-brown-700 text-white border-brown-700'
                      : 'bg-white text-brown-700 border-brown-200 hover:bg-brown-50'
                  }`}
                >
                  Related verse
                </button>
                <button
                  type="button"
                  onClick={() => setJesusActiveTab('teachings')}
                  className={`px-3 py-1.5 text-xs rounded-full border ${
                    jesusActiveTab === 'teachings'
                      ? 'bg-brown-700 text-white border-brown-700'
                      : 'bg-white text-brown-700 border-brown-200 hover:bg-brown-50'
                  }`}
                >
                  What Jesus would say
                </button>
              </div>

              {/* Tab content container with max height for responsiveness (keep card compact so chat stays tall) */}
              <div className="max-h-32 md:max-h-40 overflow-y-auto pr-1 space-y-2">
                {jesusActiveTab === 'verse' ? (
                  chatbot?.bibleVerse ? (
                    <blockquote className="pl-3 border-l-4 border-gold-500 italic bg-white/70 rounded-r-lg py-1.5 pr-3 text-xs md:text-sm">
                      {chatbot.bibleVerse}
                    </blockquote>
                  ) : (
                    <p className="text-[11px] md:text-xs text-brown-600">
                      No specific verse has been attached yet. On your dashboard, run the Christian Content analysis to
                      generate a verse connected to this site.
                    </p>
                  )
                ) : jesusPreviewLoading ? (
                  <p className="text-[11px] md:text-xs text-brown-600">Loading teachings…</p>
                ) : jesusPreviewError ? (
                  <p className="text-[11px] md:text-xs text-red-600">{jesusPreviewError}</p>
                ) : jesusPreview && jesusPreview.topTeachings.length > 0 ? (
                  <>
                    {jesusPreview.topTeachings.slice(0, 2).map((t, idx) => (
                      <div
                        key={`${t.reference}-${idx}`}
                        className="bg-white/80 rounded-lg border border-brown-200 px-3 py-2"
                      >
                        <div className="text-xs font-semibold text-gold-800">{t.reference}</div>
                        <div className="text-[11px] md:text-xs text-brown-700 mt-1 line-clamp-3">{t.text}</div>
                      </div>
                    ))}
                    {jesusPreview.topTeachings.length > 2 && (
                      <p className="text-[11px] text-brown-600 mt-1">
                        More teachings are available in your dashboard under &quot;What Jesus Would Say&quot;.
                      </p>
                    )}
                  </>
                ) : (
                  <p className="text-[11px] md:text-xs text-brown-600">
                    No specific teachings preview is available yet. You can enable and preview &quot;What Jesus Would
                    Say&quot; from the dashboard settings.
                  </p>
                )}
              </div>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}

