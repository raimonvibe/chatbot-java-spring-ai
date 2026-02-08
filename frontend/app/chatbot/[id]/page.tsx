'use client';

import { useState, useRef, useEffect } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useParams } from 'next/navigation';
import Message from '@/components/Message';
import { sendMessage, getChatbot, getQuickReplies, type Message as MessageType, type Chatbot } from '@/lib/api';
import Link from 'next/link';
import { BookOpen } from 'lucide-react';

export default function ChatbotPreview() {
  const params = useParams();
  const chatbotId = parseInt(params.id as string);

  const [chatbot, setChatbot] = useState<Chatbot | null>(null);
  const [messages, setMessages] = useState<MessageType[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [sessionId, setSessionId] = useState<string>('');
  const [quickReplies, setQuickReplies] = useState<string[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    // Load chatbot info
    getChatbot(chatbotId)
      .then((data) => {
        setChatbot(data);
        setMessages([
          {
            id: '1',
            role: 'assistant',
            content: `Hello! I'm ${data.name}. ${data.description}`,
            timestamp: Date.now(),
          },
        ]);
      })
      .catch(console.error);

    // Load quick replies
    getQuickReplies(chatbotId)
      .then(setQuickReplies)
      .catch(console.error);
  }, [chatbotId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSendMessage = async (messageText?: string) => {
    const messageToSend = messageText || input.trim();
    if (!messageToSend || isLoading) return;

    const userMessage: MessageType = {
      id: Date.now().toString(),
      role: 'user',
      content: messageToSend,
      timestamp: Date.now(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInput('');
    setIsLoading(true);

    try {
      const response = await sendMessage(chatbotId, messageToSend, sessionId);

      if (response.sessionId && !sessionId) {
        setSessionId(response.sessionId);
      }

      const assistantMessage: MessageType = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: response.message,
        timestamp: response.timestamp,
      };

      setMessages((prev) => [...prev, assistantMessage]);
    } catch (error) {
      console.error('Error sending message:', error);
      const errorMsg = error instanceof Error ? error.message : 'Unknown error occurred';
      const errorMessage: MessageType = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: `Sorry, I encountered an error: ${errorMsg}. Please try again.`,
        timestamp: Date.now(),
      };
      setMessages((prev) => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSendMessage();
    }
  };

  return (
    <main className="min-h-screen bg-gradient-to-br from-brown-50 via-amber-50/30 to-gold-50 p-4">
      <div className="max-w-4xl mx-auto">
        <div className="mb-6 flex justify-between items-center">
          <div>
            <h1 className="text-4xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-brown-600 to-gold-600 mb-2">
              {chatbot?.name || 'Loading...'}
            </h1>
            <p className="text-brown-700">{chatbot?.description}</p>
          </div>
          <Link
            href="/dashboard"
            className="px-4 py-2 bg-brown-100 text-brown-800 rounded-lg hover:bg-brown-200 transition-colors border border-brown-200"
          >
            Back to Dashboard
          </Link>
        </div>

        {(chatbot?.jesusTeachingsEnabled || chatbot?.bibleVerse) && (
          <motion.div
            initial={{ opacity: 0, y: 8 }}
            animate={{ opacity: 1, y: 0 }}
            className="mb-6 rounded-2xl overflow-hidden border-2 border-brown-200 bg-gradient-to-br from-brown-50 via-amber-50/50 to-gold-50 shadow-lg"
          >
            <div className="flex items-start gap-4 p-5">
              <div className="flex-shrink-0 w-12 h-12 rounded-xl bg-gradient-to-br from-brown-500 to-gold-600 flex items-center justify-center shadow-md">
                <BookOpen className="w-6 h-6 text-white" strokeWidth={2} />
              </div>
              <div className="min-w-0 flex-1">
                <h2 className="text-lg font-bold text-brown-800 mb-1">What Jesus Would Say</h2>
                <p className="text-sm text-brown-600 mb-2">
                  This chatbot weaves in inspiration from Jesus&apos;s teachings from the Gospels.
                </p>
                {chatbot.bibleVerse && (
                  <blockquote className="text-brown-800 text-sm md:text-base pl-3 border-l-4 border-gold-500 italic bg-white/60 rounded-r-lg py-2 pr-3">
                    {chatbot.bibleVerse}
                  </blockquote>
                )}
              </div>
            </div>
          </motion.div>
        )}

        <motion.div
          className="bg-white/90 backdrop-blur-lg rounded-3xl shadow-2xl overflow-hidden border-2 border-brown-200"
          initial={{ opacity: 0, scale: 0.98 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.4, ease: 'easeOut' }}
        >
          <div className="h-[500px] overflow-y-auto p-6 bg-gradient-to-b from-brown-50/40 to-gold-50/30">
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
                <div className="bg-brown-100 rounded-2xl px-4 py-3 shadow-md border border-brown-200">
                  <div className="flex space-x-2">
                    {[0, 1, 2].map((i) => (
                      <motion.div
                        key={i}
                        className="w-2 h-2 bg-brown-500 rounded-full"
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
            <div className="px-6 py-3 border-t-2 border-brown-200 bg-brown-50/60">
              <div className="flex flex-wrap gap-2">
                {quickReplies.map((reply, index) => (
                  <button
                    key={index}
                    onClick={() => handleSendMessage(reply)}
                    className="px-3 py-1.5 text-sm bg-gradient-to-r from-brown-100 to-gold-100 text-brown-800 rounded-full hover:from-brown-200 hover:to-gold-200 transition-colors border border-brown-200"
                    disabled={isLoading}
                  >
                    {reply}
                  </button>
                ))}
              </div>
            </div>
          )}

          <div className="p-4 border-t-2 border-brown-200 bg-brown-100/50">
            <div className="flex gap-2">
              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyPress={handleKeyPress}
                placeholder="Type your message..."
                disabled={isLoading}
                className="flex-1 px-4 py-3 rounded-xl border-2 border-brown-200 focus:outline-none focus:ring-2 focus:ring-brown-400 focus:border-brown-400 disabled:opacity-50 bg-white text-brown-900 placeholder:text-brown-400"
              />
              <button
                onClick={() => handleSendMessage()}
                disabled={!input.trim() || isLoading}
                className="px-6 py-3 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-xl font-medium disabled:opacity-50 hover:shadow-lg transition-all"
              >
                <svg
                  xmlns="http://www.w3.org/2000/svg"
                  fill="none"
                  viewBox="0 0 24 24"
                  strokeWidth={2}
                  stroke="currentColor"
                  className="w-5 h-5"
                >
                  <path
                    strokeLinecap="round"
                    strokeLinejoin="round"
                    d="M6 12L3.269 3.126A59.768 59.768 0 0121.485 12 59.77 59.77 0 013.27 20.876L5.999 12zm0 0h7.5"
                  />
                </svg>
              </button>
            </div>
          </div>
        </motion.div>
      </div>
    </main>
  );
}
