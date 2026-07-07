'use client';

import { useEffect, useState } from 'react';
import {
  getChatbot,
  getQuickReplies,
  getAnalysisStatus,
  pollUntilAnalysisReady,
  previewJesusTeachings,
  getUserFacingFetchError,
  isApiError,
  logClientIssue,
  type Chatbot,
  type JesusTeachingsPreviewResponse,
  type AnalysisStatus,
} from '@/lib/api';

export interface UseChatbotPreviewOptions {
  chatbotId: number | null;
  pathname: string;
  enabled: boolean;
  onChatbotLoaded?: (chatbot: Chatbot) => void;
}

export interface UseChatbotPreviewResult {
  chatbot: Chatbot | null;
  loadError: string | null;
  analysisLoading: boolean;
  quickReplies: string[];
  jesusPreview: JesusTeachingsPreviewResponse | null;
  jesusPreviewLoading: boolean;
  jesusPreviewError: string | null;
}

export function useChatbotPreview({
  chatbotId,
  pathname,
  enabled,
  onChatbotLoaded,
}: UseChatbotPreviewOptions): UseChatbotPreviewResult {
  const [chatbot, setChatbot] = useState<Chatbot | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [analysisLoading, setAnalysisLoading] = useState(true);
  const [quickReplies, setQuickReplies] = useState<string[]>([]);
  const [jesusPreview, setJesusPreview] = useState<JesusTeachingsPreviewResponse | null>(null);
  const [jesusPreviewLoading, setJesusPreviewLoading] = useState(false);
  const [jesusPreviewError, setJesusPreviewError] = useState<string | null>(null);

  useEffect(() => {
    if (!enabled || chatbotId === null) return;
    let cancelled = false;

    Promise.allSettled([getChatbot(chatbotId), getAnalysisStatus(chatbotId)])
      .then(async (results) => {
        if (cancelled) return;
        const chatResult = results[0];
        const statusResult = results[1];

        if (chatResult.status === 'rejected') {
          const err = chatResult.reason;
          logClientIssue('chatbotPreview.load', err);
          if (isApiError(err) && err.status === 401) {
            setLoadError('unauthorized');
            setAnalysisLoading(false);
            return;
          }
          setLoadError(getUserFacingFetchError(err, 'Could not load this chatbot. Please try again.'));
          setAnalysisLoading(false);
          return;
        }

        setLoadError(null);
        const data = chatResult.value;
        setChatbot(data);
        onChatbotLoaded?.(data);
        if (!cancelled) setAnalysisLoading(false);

        if (data.websiteUrl?.trim()) {
          void (async () => {
            try {
              let statusSnapshot: AnalysisStatus =
                statusResult.status === 'fulfilled'
                  ? statusResult.value
                  : { ready: false, pagesIndexed: 0 };
              if (statusResult.status === 'rejected') {
                try {
                  statusSnapshot = await getAnalysisStatus(chatbotId);
                } catch {
                  /* poll below */
                }
              }
              if (!statusSnapshot.ready) {
                await pollUntilAnalysisReady(chatbotId);
              }
            } catch (e) {
              logClientIssue('chatbotPreview.backgroundAnalysisPoll', e);
            }
          })();
        }

        if (data.jesusTeachingsEnabled) {
          setJesusPreviewLoading(true);
          setJesusPreviewError(null);
          previewJesusTeachings(chatbotId, 3)
            .then((preview) => {
              if (!cancelled) setJesusPreview(preview);
            })
            .catch((err) => {
              logClientIssue('chatbotPreview.jesusPreview', err);
              if (!cancelled) setJesusPreviewError('Could not load Jesus teachings preview.');
            })
            .finally(() => {
              if (!cancelled) setJesusPreviewLoading(false);
            });
        }
      })
      .catch((err) => {
        logClientIssue('chatbotPreview.load', err);
        if (!cancelled) setAnalysisLoading(false);
      });

    getQuickReplies(chatbotId)
      .then((replies) => {
        if (!cancelled) setQuickReplies(replies);
      })
      .catch((e) => logClientIssue('chatbotPreview.quickReplies', e));

    return () => {
      cancelled = true;
    };
  }, [chatbotId, enabled, pathname, onChatbotLoaded]);

  useEffect(() => {
    if (!enabled || chatbotId === null) return;
    const onVisible = () => {
      if (document.visibilityState !== 'visible') return;
      getChatbot(chatbotId)
        .then((data) => setChatbot(data))
        .catch((e) => logClientIssue('chatbotPreview.refetchOnVisible', e));
    };
    document.addEventListener('visibilitychange', onVisible);
    return () => document.removeEventListener('visibilitychange', onVisible);
  }, [chatbotId, enabled]);

  return {
    chatbot,
    loadError,
    analysisLoading,
    quickReplies,
    jesusPreview,
    jesusPreviewLoading,
    jesusPreviewError,
  };
}
