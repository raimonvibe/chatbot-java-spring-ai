'use client';

import { RefObject } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import Message from '@/components/Message';
import type { Message as MessageType } from '@/lib/api';
import type { ChatbotTheme } from '@/lib/chatbot-preview-utils';

interface EmbedChatWidgetProps {
  chatbotName?: string;
  avatarId?: string | null;
  theme: ChatbotTheme;
  messages: MessageType[];
  quickReplies: string[];
  input: string;
  isLoading: boolean;
  isMobilePreview?: boolean;
  messagesContainerRef?: RefObject<HTMLDivElement | null>;
  messagesEndRef?: RefObject<HTMLDivElement | null>;
  onInputChange: (value: string) => void;
  onSend: (text?: string) => void;
  onKeyDown: (e: React.KeyboardEvent) => void;
  headerRight?: React.ReactNode;
  topSlot?: React.ReactNode;
}

export default function EmbedChatWidget({
  chatbotName,
  avatarId,
  theme,
  messages,
  quickReplies,
  input,
  isLoading,
  isMobilePreview = false,
  messagesContainerRef,
  messagesEndRef,
  onInputChange,
  onSend,
  onKeyDown,
  headerRight,
  topSlot,
}: EmbedChatWidgetProps) {
  return (
    <div className="h-full flex flex-col overflow-hidden">
      {topSlot}
      <div
        className="flex items-center justify-between gap-2 px-3 py-2.5 sm:px-4 sm:py-3 text-white shrink-0"
        style={{ backgroundColor: theme.primaryColor }}
      >
        <div className="min-w-0 flex-1 text-left text-sm font-semibold leading-snug text-pretty line-clamp-2 sm:text-base sm:leading-normal md:line-clamp-none md:truncate">
          {chatbotName ?? 'AI Assistant'}
        </div>
        {headerRight}
      </div>

      <div
        ref={messagesContainerRef}
        className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden px-3 py-3 sm:px-[15px] sm:py-[15px] bg-gradient-to-b from-brown-50/40 to-gold-50/30 custom-scrollbar"
      >
        <AnimatePresence mode="popLayout">
          {messages.map((message, index) => (
            <Message
              key={message.id}
              message={message}
              index={index}
              primaryColor={theme.primaryColor}
              secondaryColor={theme.secondaryColor}
              assistantAvatarId={avatarId}
            />
          ))}
        </AnimatePresence>
        {isLoading && (
          <motion.div initial={{ opacity: 0 }} animate={{ opacity: 1 }} exit={{ opacity: 0 }} className="flex justify-start mb-4">
            <div
              className="rounded-2xl px-4 py-3 shadow-md border"
              style={{ backgroundColor: `${theme.secondaryColor}55`, borderColor: `${theme.secondaryColor}aa` }}
            >
              <div className="flex space-x-2">
                {[0, 1, 2].map((i) => (
                  <motion.div
                    key={i}
                    className="w-2 h-2 rounded-full"
                    style={{ backgroundColor: theme.primaryColor }}
                    animate={{ scale: [1, 1.2, 1], opacity: [0.7, 1, 0.7] }}
                    transition={{ duration: 0.6, repeat: Infinity, delay: i * 0.2 }}
                  />
                ))}
              </div>
            </div>
          </motion.div>
        )}
        <div ref={messagesEndRef} />
      </div>

      {quickReplies.length > 0 && (
        <div className="flex-shrink-0 px-3 sm:px-[15px] py-2 border-t border-brown-200/80 bg-brown-50/60">
          <div className="flex flex-wrap gap-2">
            {quickReplies.map((reply, index) => (
              <button
                key={index}
                type="button"
                onClick={() => onSend(reply)}
                className="px-3 py-1.5 text-xs rounded-full transition-colors border"
                style={{
                  backgroundColor: `${theme.secondaryColor}66`,
                  color: '#4a3828',
                  borderColor: `${theme.secondaryColor}aa`,
                }}
                disabled={isLoading}
              >
                {reply}
              </button>
            ))}
          </div>
        </div>
      )}

      <div className="flex-shrink-0 px-3 py-3 sm:px-[15px] sm:py-[15px] border-t border-brown-200/80 bg-white">
        <div className={`flex gap-2 min-w-0 items-center ${isMobilePreview ? '' : 'sm:gap-[10px]'}`}>
          <input
            type="text"
            value={input}
            onChange={(e) => onInputChange(e.target.value)}
            onKeyDown={onKeyDown}
            placeholder="Type your message..."
            className="min-w-0 flex-1 px-3 py-2 rounded-[20px] border focus:outline-none focus:ring-2 bg-white text-brown-900 placeholder:text-brown-400 text-sm"
            style={{ borderColor: `${theme.secondaryColor}cc` }}
          />
          <button
            type="button"
            onClick={() => onSend()}
            disabled={!input.trim() || isLoading}
            className="flex-shrink-0 text-white rounded-full font-medium disabled:opacity-50 hover:shadow-lg transition-all w-10 h-10 min-w-[40px] min-h-[40px] flex items-center justify-center"
            style={{ backgroundColor: theme.primaryColor }}
            aria-label="Send message"
            aria-busy={isLoading}
          >
            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" className="w-4 h-4">
              <path strokeLinecap="round" strokeLinejoin="round" d="M6 12L3.269 3.126A59.768 59.768 0 0121.485 12 59.77 59.77 0 013.27 20.876L5.999 12zm0 0h7.5" />
            </svg>
          </button>
        </div>
      </div>
    </div>
  );
}
