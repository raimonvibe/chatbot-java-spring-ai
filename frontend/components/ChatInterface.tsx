'use client';

import { useEffect, useRef, useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import Message from './Message';
import { getQuickReplies, logClientIssue } from '@/lib/api';
import { useChatSession } from '@/hooks/useChatSession';
import { Send, Book } from 'lucide-react';
import { DotLoader } from 'react-spinners';

interface ChatInterfaceProps {
  chatbotId: number;
  welcomeMessage?: string;
}

export default function ChatInterface({ chatbotId, welcomeMessage = 'Hello! How can I help you today?' }: ChatInterfaceProps) {
  const {
    messages,
    resetMessages,
    input,
    setInput,
    isLoading,
    handleSendMessage,
    handleKeyDown,
  } = useChatSession({
    chatbotId,
    initialMessages: [
      {
        id: '1',
        role: 'assistant',
        content: welcomeMessage,
        timestamp: Date.now(),
      },
    ],
  });
  const [quickReplies, setQuickReplies] = useState<string[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    resetMessages([
      {
        id: '1',
        role: 'assistant',
        content: welcomeMessage,
        timestamp: Date.now(),
      },
    ]);
  }, [chatbotId, welcomeMessage, resetMessages]);

  useEffect(() => {
    getQuickReplies(chatbotId)
      .then(setQuickReplies)
      .catch((e) => logClientIssue('chat.quickReplies', e));
  }, [chatbotId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  return (
    <motion.div
      className="bg-brown-50/90 backdrop-blur-lg rounded-3xl shadow-2xl overflow-hidden border-2 border-brown-300"
      initial={{ opacity: 0, scale: 0.9 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ duration: 0.5 }}
    >
      <div className="bg-gradient-to-r from-brown-600 to-gold-600 px-6 py-4 flex items-center gap-2 border-b-2 border-brown-700">
        <Book className="w-6 h-6 text-white" strokeWidth={2} />
        <h3 className="text-white font-semibold text-lg">Prayer-Chat Assistant</h3>
      </div>

      <div className="h-[500px] overflow-y-auto p-6 custom-scrollbar bg-gradient-to-b from-brown-50/50 to-brown-100/50">
        <AnimatePresence mode="popLayout">
          {messages.map((message, index) => (
            <Message key={message.id} message={message} index={index} />
          ))}
        </AnimatePresence>

        {isLoading && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            exit={{ opacity: 0 }}
            className="flex justify-start mb-4"
          >
            <div className="bg-brown-100 rounded-2xl px-4 py-3 shadow-md border border-brown-300">
              <DotLoader color="#8b4513" size={40} speedMultiplier={0.8} />
            </div>
          </motion.div>
        )}

        <div ref={messagesEndRef} />
      </div>

      {quickReplies.length > 0 && (
        <div className="px-6 py-3 border-t-2 border-brown-200 bg-brown-50/50">
          <div className="flex flex-wrap gap-2">
            {quickReplies.map((reply, index) => (
              <motion.button
                key={index}
                initial={{ opacity: 0, scale: 0.9 }}
                animate={{ opacity: 1, scale: 1 }}
                transition={{ delay: index * 0.1 }}
                whileHover={{ scale: 1.05 }}
                whileTap={{ scale: 0.95 }}
                onClick={() => void handleSendMessage(reply)}
                className="px-3 py-1.5 text-sm bg-gradient-to-r from-brown-100 to-gold-100 text-brown-800 rounded-full hover:from-brown-200 hover:to-gold-200 transition-colors border border-brown-300"
                disabled={isLoading}
              >
                {reply}
              </motion.button>
            ))}
          </div>
        </div>
      )}

      <div className="p-4 border-t-2 border-brown-200 bg-brown-100/50">
        <div className="flex gap-2 min-w-0">
          <motion.input
            type="text"
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={handleKeyDown}
            placeholder="Type your message..."
            className="min-w-0 flex-1 px-4 py-3 rounded-xl border-2 border-brown-300 focus:outline-none focus:ring-2 focus:ring-brown-500 focus:border-transparent transition-all bg-white text-brown-900"
            whileFocus={{ scale: 1.01 }}
          />
          <motion.button
            onClick={() => void handleSendMessage()}
            disabled={!input.trim() || isLoading}
            aria-label="Send message"
            aria-busy={isLoading}
            className="flex-shrink-0 min-w-[48px] px-4 py-3 md:px-6 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-xl font-medium disabled:opacity-50 disabled:cursor-not-allowed hover:shadow-lg transition-all"
            whileHover={{ scale: 1.05 }}
            whileTap={{ scale: 0.95 }}
          >
            <Send className="w-5 h-5" />
          </motion.button>
        </div>
      </div>
    </motion.div>
  );
}
