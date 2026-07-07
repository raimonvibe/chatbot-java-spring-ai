'use client';

import { useState } from 'react';
import Link from 'next/link';
import { motion } from 'framer-motion';
import { Book, Eye, Code, Crown, Trash2, Loader2 } from 'lucide-react';
import ThemePicker, { type PastelTheme, PASTEL_PRESETS } from '@/components/ThemePicker';
import AvatarPicker from '@/components/AvatarPicker';
import {
  getUserFacingFetchError,
  logClientIssue,
  updateChatbot,
  type AvatarId,
  type Chatbot,
  type SubscriptionStatus,
} from '@/lib/api';
interface ChatbotCardProps {
  chatbot: Chatbot;
  subscriptionStatus: SubscriptionStatus | null;
  embedFetchingId: number | null;
  chatbotDeletingId: number | null;
  onGetEmbedCode: (chatbotId: number) => void;
  onDelete: (chatbot: Chatbot) => void;
  onUpdated: (chatbot: Chatbot) => void;
  onLoadError: (message: string) => void;
}

export default function ChatbotCard({
  chatbot,
  subscriptionStatus,
  embedFetchingId,
  chatbotDeletingId,
  onGetEmbedCode,
  onDelete,
  onUpdated,
  onLoadError,
}: ChatbotCardProps) {
  const [jesusToggling, setJesusToggling] = useState(false);
  const [themeApplying, setThemeApplying] = useState(false);
  const [avatarApplying, setAvatarApplying] = useState(false);

  return (
    <motion.div
      initial={{ opacity: 0, scale: 0.98 }}
      animate={{ opacity: 1, scale: 1 }}
      className="bg-brown-50/90 backdrop-blur-sm rounded-2xl shadow-sm p-6 hover:shadow transition-all border border-brown-100"
    >
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <Book className="w-5 h-5 text-brown-700 flex-shrink-0" />
          <h3 className="text-xl font-bold text-brown-800">{chatbot.name}</h3>
        </div>
      </div>
      <p className="text-brown-700 mb-4">{chatbot.description}</p>

      <div className="space-y-2">
        <Link
          href={`/chatbot/${chatbot.id}`}
          className="flex items-center justify-center gap-2 w-full px-4 py-2.5 bg-brown-100 text-brown-800 rounded-lg hover:bg-brown-200 transition-colors font-medium cursor-pointer"
        >
          <Eye className="w-4 h-4" />
          Preview Chatbot
        </Link>

        <button
          type="button"
          disabled={embedFetchingId === chatbot.id}
          onClick={() => onGetEmbedCode(chatbot.id)}
          className={`flex items-center justify-center gap-2 w-full px-4 py-2.5 rounded-lg transition-colors font-medium cursor-pointer disabled:opacity-60 disabled:cursor-not-allowed ${
            subscriptionStatus?.isPreviewMode
              ? 'bg-brown-100 text-brown-600 hover:bg-brown-200'
              : 'bg-gold-100 text-gold-800 hover:bg-gold-200'
          }`}
        >
          {embedFetchingId === chatbot.id ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin shrink-0" aria-hidden />
              Loading…
            </>
          ) : subscriptionStatus?.isPreviewMode ? (
            <>
              <Crown className="w-4 h-4 shrink-0" />
              Website embed snippet
            </>
          ) : (
            <>
              <Code className="w-4 h-4 shrink-0" />
              Get Embed Code
            </>
          )}
        </button>

        <button
          type="button"
          title="Delete chatbot"
          disabled={chatbotDeletingId === chatbot.id || embedFetchingId === chatbot.id}
          onClick={() => onDelete(chatbot)}
          className="flex items-center justify-center gap-2 w-full px-4 py-2.5 rounded-lg border border-brown-200 bg-white text-brown-700 hover:bg-red-50 hover:text-red-900 hover:border-red-200/80 transition-colors font-medium cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
        >
          {chatbotDeletingId === chatbot.id ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin shrink-0" aria-hidden />
              Deleting…
            </>
          ) : (
            <>
              <Trash2 className="w-4 h-4 shrink-0" aria-hidden />
              Delete chatbot
            </>
          )}
        </button>
      </div>

      <div className="mt-3 pt-3 border-t border-brown-200 space-y-1.5">
        <label className="flex items-center gap-2 cursor-pointer select-none">
          <input
            type="checkbox"
            checked={chatbot.jesusTeachingsEnabled === true}
            disabled={jesusToggling}
            onChange={async () => {
              setJesusToggling(true);
              try {
                const updated = await updateChatbot(chatbot.id, {
                  ...chatbot,
                  jesusTeachingsEnabled: !chatbot.jesusTeachingsEnabled,
                });
                onUpdated(updated);
              } catch (err) {
                logClientIssue('dashboard.jesusToggle', err);
                onLoadError(getUserFacingFetchError(err, 'Could not update Jesus teachings setting. Please try again.'));
              } finally {
                setJesusToggling(false);
              }
            }}
            className="w-4 h-4 rounded border-brown-300 text-gold-600 focus:ring-gold-500 cursor-pointer flex-shrink-0 mt-0.5"
          />
          <span className="text-sm font-medium text-brown-700 leading-5">
            Include &quot;What Jesus Would Say&quot;
          </span>
        </label>
        {chatbot.bibleVerse && (
          <p className="text-xs text-brown-600 italic pl-0 line-clamp-2" title={chatbot.bibleVerse}>
            {chatbot.bibleVerse}
          </p>
        )}
      </div>

      <div className="mt-4 pt-4 border-t border-brown-200">
        <AvatarPicker
          currentAvatarId={chatbot.avatarId ?? ''}
          onSelect={async (avatarId: '' | AvatarId) => {
            setAvatarApplying(true);
            try {
              const updated = await updateChatbot(chatbot.id, {
                ...chatbot,
                name: (chatbot.name && chatbot.name.trim()) || 'Chatbot',
                websiteUrl: (chatbot.websiteUrl && chatbot.websiteUrl.trim()) || 'https://example.com',
                avatarId: avatarId ? avatarId : '',
              });
              onUpdated(updated);
            } catch (err) {
              logClientIssue('dashboard.avatar.save', err);
              alert(getUserFacingFetchError(err, 'Failed to save avatar. Please try again.'));
            } finally {
              setAvatarApplying(false);
            }
          }}
          disabled={avatarApplying}
        />
        <ThemePicker
          currentBrandingConfig={chatbot.brandingConfig ?? '{}'}
          applying={themeApplying}
          onApply={async (theme: PastelTheme) => {
            if (!PASTEL_PRESETS.some((p) => p.primaryColor === theme.primaryColor && p.secondaryColor === theme.secondaryColor)) {
              return;
            }
            setThemeApplying(true);
            try {
              const merged: Record<string, string> = {};
              if (chatbot.brandingConfig) {
                try {
                  const existing = JSON.parse(chatbot.brandingConfig) as Record<string, unknown>;
                  if (typeof existing.fontFamily === 'string') merged.fontFamily = existing.fontFamily;
                } catch {
                  /* ignore */
                }
              }
              merged.primaryColor = theme.primaryColor;
              merged.secondaryColor = theme.secondaryColor;
              if (theme.borderRadius) merged.borderRadius = theme.borderRadius;
              const updated = await updateChatbot(chatbot.id, {
                ...chatbot,
                name: (chatbot.name && chatbot.name.trim()) || 'Chatbot',
                websiteUrl: (chatbot.websiteUrl && chatbot.websiteUrl.trim()) || 'https://example.com',
                brandingConfig: JSON.stringify(merged),
              });
              onUpdated(updated);
            } catch (err) {
              logClientIssue('dashboard.theme.save', err);
              alert(getUserFacingFetchError(err, 'Failed to save theme. Please try again.'));
            } finally {
              setThemeApplying(false);
            }
          }}
        />
      </div>
    </motion.div>
  );
}
