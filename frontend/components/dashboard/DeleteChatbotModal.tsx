'use client';

import { motion } from 'framer-motion';
import { Trash2, X, Loader2 } from 'lucide-react';
import type { Chatbot, SubscriptionStatus } from '@/lib/api';
import { deleteModalWebsiteScanNote } from '@/lib/api';

interface DeleteChatbotModalProps {
  chatbot: Chatbot;
  subscriptionStatus: SubscriptionStatus | null;
  deleting: boolean;
  error: string | null;
  onClose: () => void;
  onConfirm: () => void;
}

export default function DeleteChatbotModal({
  chatbot,
  subscriptionStatus,
  deleting,
  error,
  onClose,
  onConfirm,
}: DeleteChatbotModalProps) {
  return (
    <motion.div
      key="delete-chatbot-modal"
      role="presentation"
      className="fixed inset-0 z-[100] flex items-center justify-center p-4 sm:p-6"
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.15 }}
    >
      <button
        type="button"
        className="absolute inset-0 bg-black/45 backdrop-blur-[2px] cursor-default"
        aria-label="Close delete confirmation"
        disabled={deleting}
        onClick={onClose}
      />
      <motion.div
        role="dialog"
        aria-modal="true"
        aria-labelledby="delete-chatbot-dialog-title"
        aria-describedby="delete-chatbot-dialog-desc"
        initial={{ opacity: 0, scale: 0.97, y: 6 }}
        animate={{ opacity: 1, scale: 1, y: 0 }}
        exit={{ opacity: 0, scale: 0.97, y: 6 }}
        transition={{ type: 'spring', stiffness: 380, damping: 28 }}
        className="relative z-10 w-full max-w-md rounded-2xl border border-brown-200 bg-white shadow-xl shadow-brown-900/10 overflow-hidden"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="p-6 sm:p-7">
          <div className="flex items-start justify-between gap-3 mb-4">
            <div className="flex h-11 w-11 shrink-0 items-center justify-center rounded-xl bg-red-50 text-red-700 border border-red-100">
              <Trash2 className="h-5 w-5" aria-hidden />
            </div>
            <button
              type="button"
              className="rounded-lg p-2 text-brown-500 hover:bg-brown-100 hover:text-brown-800 disabled:opacity-40"
              aria-label="Close"
              disabled={deleting}
              onClick={onClose}
            >
              <X className="h-5 w-5" />
            </button>
          </div>
          <h2 id="delete-chatbot-dialog-title" className="text-xl font-bold text-brown-900 tracking-tight">
            Delete this chatbot?
          </h2>
          <p id="delete-chatbot-dialog-desc" className="mt-3 text-sm text-brown-700 leading-relaxed">
            You&apos;re about to remove <span className="font-semibold text-brown-900">{chatbot.name}</span>. This
            cannot be undone.
          </p>
          <p className="mt-3 text-sm text-brown-600 leading-relaxed rounded-xl bg-amber-50/90 border border-amber-100 px-3 py-2.5">
            {deleteModalWebsiteScanNote(subscriptionStatus)}
          </p>
          {error && (
            <div role="alert" className="mt-4 text-sm text-red-800 bg-red-50 border border-red-200 rounded-xl px-3 py-2.5">
              {error}
            </div>
          )}
          <div className="mt-6 flex flex-col-reverse sm:flex-row sm:justify-end gap-2 sm:gap-3">
            <button
              type="button"
              className="w-full sm:w-auto px-4 py-2.5 rounded-xl border border-brown-200 bg-white text-brown-800 font-medium hover:bg-brown-50 disabled:opacity-50"
              disabled={deleting}
              onClick={onClose}
            >
              Cancel
            </button>
            <button
              type="button"
              className="w-full sm:w-auto px-4 py-2.5 rounded-xl bg-red-700 text-white font-semibold hover:bg-red-800 disabled:opacity-60 flex items-center justify-center gap-2"
              disabled={deleting}
              onClick={onConfirm}
            >
              {deleting ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin shrink-0" aria-hidden />
                  Deleting…
                </>
              ) : (
                'Delete permanently'
              )}
            </button>
          </div>
        </div>
      </motion.div>
    </motion.div>
  );
}
