'use client';

import { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { BookOpen, Sparkles, X } from 'lucide-react';
import { ClipLoader } from 'react-spinners';
import { updateChatbot, previewJesusTeachings, type Chatbot, type JesusTeachingsPreviewResponse } from '@/lib/api';

interface JesusTeachingsSettingsProps {
  chatbot: Chatbot;
  onUpdate: (updated: Chatbot) => void;
}

export default function JesusTeachingsSettings({ chatbot, onUpdate }: JesusTeachingsSettingsProps) {
  const [saving, setSaving] = useState(false);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [preview, setPreview] = useState<JesusTeachingsPreviewResponse | null>(null);
  const [showPreviewModal, setShowPreviewModal] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const enabled = chatbot.jesusTeachingsEnabled === true;

  const handleToggle = async () => {
    setSaving(true);
    setError(null);
    try {
      const updated = await updateChatbot(chatbot.id, {
        ...chatbot,
        jesusTeachingsEnabled: !enabled,
      });
      onUpdate(updated);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to update setting');
    } finally {
      setSaving(false);
    }
  };

  const handlePreview = async () => {
    setPreviewLoading(true);
    setError(null);
    setPreview(null);
    setShowPreviewModal(true);
    try {
      const data = await previewJesusTeachings(chatbot.id, 5);
      setPreview(data);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to load preview');
    } finally {
      setPreviewLoading(false);
    }
  };

  return (
    <div className="bg-white rounded-lg shadow p-5 border border-brown-200">
      <div className="flex items-center gap-3 mb-4">
        <div className="p-2 bg-gradient-to-br from-brown-100 to-gold-100 rounded-lg">
          <BookOpen className="w-5 h-5 text-brown-700" />
        </div>
        <div>
          <h4 className="font-bold text-brown-800">What Jesus Would Say</h4>
          <p className="text-sm text-brown-600">
            Include Jesus&apos;s teachings in chatbot responses based on your website
          </p>
        </div>
      </div>

      <div className="space-y-3">
        <label className="flex items-center gap-3 cursor-pointer">
          <input
            type="checkbox"
            checked={enabled}
            onChange={handleToggle}
            disabled={saving}
            className="w-4 h-4 rounded border-brown-300 text-gold-600 focus:ring-gold-500"
          />
          <span className="text-brown-700 font-medium">
            Include &quot;What Jesus Would Say&quot; in responses
          </span>
        </label>

        <button
          type="button"
          onClick={handlePreview}
          disabled={previewLoading}
          className="flex items-center gap-2 px-4 py-2 bg-brown-100 text-brown-800 rounded-lg hover:bg-brown-200 transition-colors text-sm font-medium disabled:opacity-50"
        >
          {previewLoading ? (
            <ClipLoader size={16} color="#78350f" />
          ) : (
            <Sparkles className="w-4 h-4" />
          )}
          Preview Teachings
        </button>

        {error && (
          <p className="text-sm text-red-600" role="alert">
            {error}
          </p>
        )}
      </div>

      <AnimatePresence>
        {showPreviewModal && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50"
            onClick={() => setShowPreviewModal(false)}
          >
            <motion.div
              initial={{ scale: 0.95, opacity: 0 }}
              animate={{ scale: 1, opacity: 1 }}
              exit={{ scale: 0.95, opacity: 0 }}
              className="bg-brown-50 rounded-2xl p-6 max-w-lg w-full border-2 border-brown-300 shadow-2xl max-h-[85vh] flex flex-col"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-center justify-between mb-4">
                <h3 className="text-xl font-bold text-brown-800 flex items-center gap-2">
                  <BookOpen className="w-6 h-6 text-gold-600" />
                  Preview: What Jesus Would Say
                </h3>
                <button
                  type="button"
                  onClick={() => setShowPreviewModal(false)}
                  className="p-2 rounded-lg hover:bg-brown-200 text-brown-700"
                  aria-label="Close"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>

              {previewLoading && !preview ? (
                <div className="flex items-center justify-center py-8">
                  <ClipLoader color="#78350f" size={32} />
                </div>
              ) : preview ? (
                <div className="overflow-y-auto flex-1 space-y-4">
                  {preview.websiteUrl && (
                    <p className="text-sm text-brown-600">
                      For: <span className="font-medium">{preview.websiteUrl}</span>
                    </p>
                  )}
                  <p className="text-sm text-brown-700">
                    Top teachings that would be used in responses ({preview.totalJesusVerses} Jesus
                    verses in database):
                  </p>
                  <ul className="space-y-3">
                    {preview.topTeachings.map((t, i) => (
                      <li
                        key={`${t.reference}-${i}`}
                        className="bg-white rounded-lg p-4 border border-brown-200"
                      >
                        <div className="font-semibold text-gold-800">{t.reference}</div>
                        <p className="text-brown-700 text-sm mt-1 line-clamp-3">{t.text}</p>
                        <p className="text-xs text-brown-500 mt-2">
                          Relevance: {(parseFloat(t.similarity) * 100).toFixed(0)}%
                        </p>
                      </li>
                    ))}
                  </ul>
                  {preview.topTeachings.length === 0 && (
                    <p className="text-brown-600 text-sm">
                      No teachings matched yet. Enable the feature and chat with your chatbot to see
                      relevant teachings.
                    </p>
                  )}
                </div>
              ) : (
                <p className="text-brown-600 text-sm py-4">{error || 'Could not load preview.'}</p>
              )}

              <div className="mt-4 pt-4 border-t border-brown-200">
                <button
                  type="button"
                  onClick={() => setShowPreviewModal(false)}
                  className="w-full px-4 py-2 bg-brown-200 text-brown-800 rounded-lg hover:bg-brown-300 font-medium"
                >
                  Close
                </button>
              </div>
            </motion.div>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  );
}
