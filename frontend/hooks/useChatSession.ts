'use client';

import { useCallback, useRef, useState } from 'react';
import { getUserFacingFetchError, logClientIssue, sendMessage, type Message } from '@/lib/api';

export interface UseChatSessionOptions {
  chatbotId: number | null;
  initialMessages?: Message[];
  enabled?: boolean;
}

export function useChatSession({ chatbotId, initialMessages = [], enabled = true }: UseChatSessionOptions) {
  const [messages, setMessages] = useState<Message[]>(initialMessages);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [sessionId, setSessionId] = useState('');
  const sessionIdRef = useRef('');
  const sendingRef = useRef(false);

  const resetMessages = useCallback((next: Message[]) => {
    setMessages(next);
    setSessionId('');
    sessionIdRef.current = '';
  }, []);

  const handleSendMessage = useCallback(
    async (messageText?: string) => {
      const messageToSend = (messageText ?? input).trim();
      if (!messageToSend || isLoading || sendingRef.current || !enabled || chatbotId === null) return;
      sendingRef.current = true;

      const userMessage: Message = {
        id: Date.now().toString(),
        role: 'user',
        content: messageToSend,
        timestamp: Date.now(),
      };

      setMessages((prev) => [...prev, userMessage]);
      setInput('');
      setIsLoading(true);

      try {
        const userLanguage =
          typeof navigator !== 'undefined' ? navigator.language?.split('-')[0] || 'en' : 'en';
        const activeSessionId = sessionIdRef.current || sessionId;
        const response = await sendMessage(
          chatbotId,
          messageToSend,
          activeSessionId || undefined,
          userLanguage
        );

        if (response.sessionId) {
          sessionIdRef.current = response.sessionId;
          setSessionId(response.sessionId);
        }

        setMessages((prev) => [
          ...prev,
          {
            id: (Date.now() + 1).toString(),
            role: 'assistant',
            content: response.message,
            timestamp: response.timestamp,
          },
        ]);
      } catch (error) {
        logClientIssue('chat.send', error);
        const errorMsg = getUserFacingFetchError(error, 'Something went wrong. Please try again.');
        setMessages((prev) => [
          ...prev,
          {
            id: (Date.now() + 1).toString(),
            role: 'assistant',
            content: `Sorry, I encountered an error: ${errorMsg}`,
            timestamp: Date.now(),
          },
        ]);
      } finally {
        sendingRef.current = false;
        setIsLoading(false);
      }
    },
    [chatbotId, enabled, input, isLoading, sessionId]
  );

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        if (!isLoading) void handleSendMessage();
      }
    },
    [handleSendMessage, isLoading]
  );

  return {
    messages,
    setMessages,
    resetMessages,
    input,
    setInput,
    isLoading,
    sessionId,
    handleSendMessage,
    handleKeyDown,
  };
}
