'use client';

import { motion } from 'framer-motion';
import Link from 'next/link';
import { Code, Copy, CheckCircle, X } from 'lucide-react';
import type { Chatbot } from '@/lib/api';
import { copyTextToClipboard } from '@/lib/clipboard';

interface EmbedCodeModalProps {
  chatbot: Chatbot;
  embedCode: string;
  copyFeedback: 'idle' | 'success' | 'error';
  onCopyFeedback: (feedback: 'idle' | 'success' | 'error') => void;
  onClose: () => void;
}

export default function EmbedCodeModal({
  chatbot,
  embedCode,
  copyFeedback,
  onCopyFeedback,
  onClose,
}: EmbedCodeModalProps) {
  return (
    <motion.div
      initial={{ opacity: 0 }}
      animate={{ opacity: 1 }}
      className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50 overflow-y-auto"
      onClick={onClose}
    >
      <div
        className="bg-brown-50 rounded-2xl p-6 sm:p-8 max-w-2xl w-full min-w-0 max-h-[min(90vh,40rem)] overflow-y-auto border border-brown-200 shadow-lg my-auto"
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-center gap-2 mb-2">
          <Code className="w-6 h-6 text-brown-700 flex-shrink-0" />
          <h3 className="text-xl sm:text-2xl font-bold text-brown-800 truncate min-w-0">
            Embed code for {chatbot.name}
          </h3>
        </div>
        <p className="text-brown-700 text-sm mb-3">
          Paste this snippet just before the closing{' '}
          <code className="bg-brown-200 px-1 rounded">&lt;/body&gt;</code> on your website.
        </p>
        <p className="text-brown-700 text-xs sm:text-sm mb-3">
          If the widget does not appear or styles look off, open{' '}
          <Link href="/troubleshooting" className="font-semibold text-brown-900 underline underline-offset-2 hover:text-gold-700">
            Troubleshooting
          </Link>
          .
        </p>
        <pre className="bg-brown-100 p-4 rounded-lg overflow-x-auto mb-4 border border-brown-300 text-brown-900 text-sm sm:text-base">
          <code>{embedCode}</code>
        </pre>
        <div className="flex flex-col-reverse sm:flex-row gap-3 sm:gap-4">
          <button
            type="button"
            onClick={async () => {
              const ok = await copyTextToClipboard(embedCode);
              onCopyFeedback(ok ? 'success' : 'error');
              setTimeout(() => onCopyFeedback('idle'), 2000);
            }}
            className="flex-1 min-w-0 px-4 py-2 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-lg hover:shadow-lg transition-all flex items-center justify-center gap-2"
          >
            {copyFeedback === 'success' ? <CheckCircle className="w-4 h-4" /> : <Copy className="w-4 h-4" />}
            {copyFeedback === 'success' ? 'Copied!' : copyFeedback === 'error' ? 'Copy failed' : 'Copy code'}
          </button>
          <button
            onClick={onClose}
            className="w-full sm:w-auto px-4 py-2 bg-brown-200 text-brown-800 rounded-lg hover:bg-brown-300 transition-colors flex items-center justify-center gap-2"
          >
            <X className="w-4 h-4" />
            Close
          </button>
        </div>
      </div>
    </motion.div>
  );
}
