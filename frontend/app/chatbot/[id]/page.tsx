'use client';

import { useState, useRef, useEffect, useMemo } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useParams } from 'next/navigation';
import Message from '@/components/Message';
import {
  sendMessage,
  getChatbot,
  getQuickReplies,
  pollUntilAnalysisReady,
  previewJesusTeachings,
  type Message as MessageType,
  type Chatbot,
  type JesusTeachingsPreviewResponse,
} from '@/lib/api';
import Link from 'next/link';
import { BookOpen, ChevronDown, ChevronUp } from 'lucide-react';
import CalligraphicFrame from '@/components/CalligraphicFrame';
import ChatbotCreationLoader from '@/components/ChatbotCreationLoader';

/** Validates and parses chatbot ID from URL. Returns a positive integer or null if invalid (no API calls with bad ID). */
function parseChatbotId(raw: string | string[] | undefined): number | null {
  if (raw == null) return null;
  const s = typeof raw === 'string' ? raw : raw[0];
  if (s == null || s.length === 0) return null;
  const n = parseInt(s, 10);
  if (!Number.isInteger(n) || n < 1 || !Number.isFinite(n)) return null;
  return n;
}

export default function ChatbotPreview() {
  const params = useParams();
  const chatbotId = useMemo(() => parseChatbotId(params?.id), [params?.id]);
  const isValidId = chatbotId !== null;

  const [chatbot, setChatbot] = useState<Chatbot | null>(null);
  const [messages, setMessages] = useState<MessageType[]>([]);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [sessionId, setSessionId] = useState<string>('');
  const [quickReplies, setQuickReplies] = useState<string[]>([]);
  const [jesusCardOpen, setJesusCardOpen] = useState(false);
  const [jesusActiveTab, setJesusActiveTab] = useState<'verse' | 'teachings'>('verse');
  const [jesusPreview, setJesusPreview] = useState<JesusTeachingsPreviewResponse | null>(null);
  const [jesusPreviewError, setJesusPreviewError] = useState<string | null>(null);
  const [jesusPreviewLoading, setJesusPreviewLoading] = useState(false);
  /** When true, we are still waiting for website analysis so the chatbot can answer about the site. */
  const [analysisLoading, setAnalysisLoading] = useState(true);
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const messagesContainerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!isValidId || chatbotId === null) return;
    let cancelled = false;
    getChatbot(chatbotId)
      .then(async (data) => {
        if (cancelled) return;
        setChatbot(data);
        setMessages([
          {
            id: '1',
            role: 'assistant',
            content: `Hello! I'm ${data.name}. ${data.description}`,
            timestamp: Date.now(),
          },
        ]);
        // If chatbot has a website, keep loading until analysis is ready so "tell me about this site" works
        if (data.websiteUrl?.trim()) {
          try {
            await pollUntilAnalysisReady(chatbotId);
          } finally {
            if (!cancelled) setAnalysisLoading(false);
          }
        } else {
          setAnalysisLoading(false);
        }
        // If \"What Jesus Would Say\" is enabled, load a small preview of teachings for the header card
        if (data.jesusTeachingsEnabled) {
          setJesusPreviewLoading(true);
          setJesusPreviewError(null);
          previewJesusTeachings(chatbotId, 3)
            .then(setJesusPreview)
            .catch((err) => {
              console.error(err);
              setJesusPreviewError('Could not load Jesus teachings preview.');
            })
            .finally(() => {
              setJesusPreviewLoading(false);
            });
        }
      })
      .catch((err) => {
        console.error(err);
        if (!cancelled) setAnalysisLoading(false);
      });

    getQuickReplies(chatbotId)
      .then(setQuickReplies)
      .catch(console.error);

    return () => { cancelled = true; };
  }, [chatbotId, isValidId]);

  // Scroll messages container to bottom when messages change (sends/receives)
  useEffect(() => {
    const el = messagesContainerRef.current;
    if (!el) return;
    const scrollToBottom = () => {
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
    };
    scrollToBottom();
    // After layout (new message in DOM), scroll again in case height wasn't ready
    const id = requestAnimationFrame(() => {
      requestAnimationFrame(scrollToBottom);
    });
    return () => cancelAnimationFrame(id);
  }, [messages]);

  const handleSendMessage = async (messageText?: string) => {
    const messageToSend = (messageText ?? input).trim();
    if (!messageToSend || isLoading || !isValidId || chatbotId === null) return;

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
      const response = await sendMessage(chatbotId as number, messageToSend, sessionId);

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

  const hasJesusFeature = chatbot?.jesusTeachingsEnabled || chatbot?.bibleVerse;

  if (!isValidId) {
    return (
      <main className="h-screen flex flex-col items-center justify-center bg-gradient-to-br from-brown-50 via-amber-50/30 to-gold-50 p-4">
        <div className="text-center max-w-md">
          <h1 className="text-xl font-bold text-brown-800 mb-2">Invalid chatbot</h1>
          <p className="text-brown-700 mb-4">This link is not valid. Please use a link from your dashboard.</p>
          <Link
            href="/dashboard"
            className="inline-block px-4 py-2 bg-brown-200 text-brown-800 rounded-lg hover:bg-brown-300 transition-colors"
          >
            Back to Dashboard
          </Link>
        </div>
      </main>
    );
  }

  if (analysisLoading) {
    return (
      <>
        <ChatbotCreationLoader isVisible={true} mode="analysis" chatbotName={chatbot?.name} isScanningWebsite />
      </>
    );
  }

  return (
    <main className="h-screen flex flex-col overflow-hidden md:h-auto md:min-h-[150vh] md:overflow-y-auto bg-gradient-to-br from-brown-50 via-amber-50/30 to-gold-50">
      {/* Compact header; on desktop the whole page scrolls so the chat area is taller */}
      <header className="flex-shrink-0 p-2 md:p-3 border-b border-brown-200/60 bg-white/50 backdrop-blur-sm">
        <div className="max-w-4xl mx-auto flex flex-wrap items-center justify-between gap-2">
          <div className="min-w-0 flex-1">
            <h1 className="text-xl md:text-2xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-brown-600 to-gold-600 truncate">
              {chatbot?.name ?? 'Loading...'}
            </h1>
            <p className="text-brown-700 text-sm truncate max-w-[min(100%,320px)] md:max-w-none">
              {chatbot?.description}
            </p>
          </div>
          <Link
            href="/dashboard"
            className="flex-shrink-0 px-3 py-1.5 md:px-4 md:py-2 text-sm bg-brown-100 text-brown-800 rounded-lg hover:bg-brown-200 transition-colors border border-brown-200"
          >
            Back to Dashboard
          </Link>
        </div>
      </header>

      {/* Optional Jesus card: collapsible, compact so chat keeps most of the height */}
      {hasJesusFeature && (
        <motion.div
          initial={false}
          className="flex-shrink-0 border-b border-brown-200/60 bg-gradient-to-r from-brown-50/80 to-amber-50/50"
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

                  {/* Tab content container with max height for responsiveness */}
                  <div className="max-h-40 md:max-h-48 overflow-y-auto pr-1 space-y-2">
                    {jesusActiveTab === 'verse' ? (
                      chatbot?.bibleVerse ? (
                        <blockquote className="pl-3 border-l-4 border-gold-500 italic bg-white/70 rounded-r-lg py-1.5 pr-3 text-xs md:text-sm">
                          {chatbot.bibleVerse}
                        </blockquote>
                      ) : (
                        <p className="text-[11px] md:text-xs text-brown-600">
                          No specific verse has been attached yet. On your dashboard, run the Christian Content analysis
                          to generate a verse connected to this site.
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
                        No specific teachings preview is available yet. You can enable and preview &quot;What Jesus
                        Would Say&quot; from the dashboard settings.
                      </p>
                    )}
                  </div>
                </div>
              </motion.div>
            )}
          </AnimatePresence>
        </motion.div>
      )}

      {/* Chat window: takes remaining space, no page scroll — only inner chat scrolls */}
      <div className="flex-1 min-h-0 flex flex-col p-2 md:p-3 max-w-4xl w-full mx-auto">
        <CalligraphicFrame className="flex-1 min-h-0 rounded-3xl overflow-hidden shadow-2xl border-2 border-brown-200/80 bg-white/95 backdrop-blur-sm">
          <div className="h-full flex flex-col rounded-3xl overflow-hidden p-4 md:p-5">
            {/* Scrollable messages area — only this scrolls; horizontal padding keeps book/user icons inside frame */}
                <div
              ref={messagesContainerRef}
              className="flex-1 min-h-0 overflow-y-auto overflow-x-hidden px-1 py-2 md:px-2 md:py-3 bg-gradient-to-b from-brown-50/40 to-gold-50/30 custom-scrollbar"
            >
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

            {/* Quick replies: fixed at bottom of chat panel */}
            {quickReplies.length > 0 && (
              <div className="flex-shrink-0 px-2 md:px-3 py-2 border-t border-brown-200/80 bg-brown-50/60">
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

            {/* Input: fixed at bottom, fully inside frame; slightly narrower row on desktop */}
            <div className="flex-shrink-0 pt-3 px-2 pb-0 md:px-0 md:pt-4 border-t-2 border-brown-200/80 bg-brown-100/50 relative z-10">
              <div className="flex gap-2 min-w-0 max-w-3xl mx-auto">
                <input
                  type="text"
                  value={input}
                  onChange={(e) => setInput(e.target.value)}
                  onKeyPress={handleKeyPress}
                  placeholder="Type your message..."
                  disabled={isLoading}
                  className="min-w-0 flex-1 px-4 py-3 rounded-xl border-2 border-brown-200 focus:outline-none focus:ring-2 focus:ring-brown-400 focus:border-brown-400 disabled:opacity-50 bg-white text-brown-900 placeholder:text-brown-400"
                />
                <button
                  onClick={() => handleSendMessage()}
                  disabled={!input.trim() || isLoading}
                  className="flex-shrink-0 px-4 py-3 md:px-6 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-xl font-medium disabled:opacity-50 hover:shadow-lg transition-all min-w-[48px]"
                  aria-label="Send message"
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
          </div>
        </CalligraphicFrame>
      </div>
    </main>
  );
}
