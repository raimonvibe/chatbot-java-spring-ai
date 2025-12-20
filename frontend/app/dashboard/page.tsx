'use client';

import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { getAllChatbots, createChatbot, analyzeWebsite, getEmbedCode, checkAuth, type Chatbot } from '@/lib/api';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Book, Plus, X, Eye, Code, Copy, CheckCircle } from 'lucide-react';
import ChatbotCreationLoader from '@/components/ChatbotCreationLoader';

export default function Dashboard() {
  const router = useRouter();
  const [chatbots, setChatbots] = useState<Chatbot[]>([]);
  const [loading, setLoading] = useState(true);
  const [authenticated, setAuthenticated] = useState(false);
  const [showCreateForm, setShowCreateForm] = useState(false);
  const [selectedChatbot, setSelectedChatbot] = useState<Chatbot | null>(null);
  const [embedCode, setEmbedCode] = useState('');

  const [formData, setFormData] = useState({
    name: 'Customer Support Bot',
    description: 'AI assistant to help customers with common questions and support',
    websiteUrl: 'https://example.com',
    primaryLanguage: 'en',
  });
  const [creating, setCreating] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);

  useEffect(() => {
    loadChatbots();
  }, []);

  const loadChatbots = async () => {
    try {
      const data = await getAllChatbots();
      setChatbots(data);
      setAuthenticated(true);
    } catch (error) {
      console.error('Error loading chatbots:', error);
      // If unauthorized, show login prompt
      if ((error as any).status === 401) {
        setAuthenticated(false);
      }
    } finally {
      setLoading(false);
    }
  };

  const handleCreateChatbot = async (e: React.FormEvent) => {
    e.preventDefault();
    setCreating(true);

    try {
      const newChatbot = await createChatbot(formData);
      setChatbots([...chatbots, newChatbot]);
      setFormData({ name: '', description: '', websiteUrl: '', primaryLanguage: 'en' });
      setShowCreateForm(false);

      // Auto-analyze website if URL provided
      if (formData.websiteUrl) {
        handleAnalyzeWebsite(newChatbot.id, formData.websiteUrl);
      }
    } catch (error) {
      console.error('Error creating chatbot:', error);
      alert('Failed to create chatbot');
    } finally {
      setCreating(false);
    }
  };

  const handleAnalyzeWebsite = async (chatbotId: number, websiteUrl: string) => {
    setAnalyzing(true);
    try {
      await analyzeWebsite(chatbotId, websiteUrl);
      alert('Website analysis started! This may take a few minutes.');
    } catch (error) {
      console.error('Error analyzing website:', error);
      alert('Failed to analyze website');
    } finally {
      setAnalyzing(false);
    }
  };

  const handleGetEmbedCode = async (chatbotId: number) => {
    try {
      const code = await getEmbedCode(chatbotId);
      setEmbedCode(code);
    } catch (error) {
      console.error('Error getting embed code:', error);
    }
  };

  if (loading) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center">
        <Book className="w-16 h-16 text-brown-600 animate-pulse mb-4" strokeWidth={1.5} />
        <div className="text-xl text-brown-700">Loading your chatbots...</div>
      </div>
    );
  }

  if (!authenticated) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center p-4">
        <div className="bg-brown-50/90 backdrop-blur-sm rounded-2xl shadow-xl p-8 max-w-md text-center border border-brown-200">
          <Book className="w-16 h-16 text-brown-600 mx-auto mb-4" strokeWidth={1.5} />
          <h2 className="text-2xl font-bold text-brown-800 mb-4">Authentication Required</h2>
          <p className="text-brown-700 mb-6">
            Please log in with Google to access your dashboard and create chatbots.
          </p>
          <button
            onClick={() => router.push('/login')}
            className="w-full px-6 py-3 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-lg font-semibold hover:shadow-xl transition-all"
          >
            Log In with Google
          </button>
        </div>
      </div>
    );
  }

  return (
    <main className="min-h-screen p-8">
      <ChatbotCreationLoader isVisible={creating} chatbotName={formData.name} />
      <div className="max-w-7xl mx-auto">
        <div className="flex justify-between items-center mb-8">
          <div className="flex items-center gap-3">
            <Book className="w-10 h-10 text-brown-700" strokeWidth={1.5} />
            <h1 className="text-4xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-brown-700 via-brown-600 to-gold-700">
              TjanaBot Dashboard
            </h1>
          </div>
          <button
            onClick={() => setShowCreateForm(!showCreateForm)}
            className="px-6 py-3 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-xl font-medium hover:shadow-lg transition-all flex items-center gap-2"
          >
            {showCreateForm ? (
              <><X className="w-5 h-5" /> Cancel</>
            ) : (
              <><Plus className="w-5 h-5" /> Create New Chatbot</>
            )}
          </button>
        </div>

        {showCreateForm && (
          <motion.div
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            className="bg-brown-50/90 backdrop-blur-sm rounded-2xl shadow-xl p-8 mb-8 border border-brown-200"
          >
            <div className="flex items-center gap-2 mb-6">
              <Book className="w-6 h-6 text-brown-700" />
              <h2 className="text-2xl font-bold text-brown-800">Create New Chatbot</h2>
            </div>
            <form onSubmit={handleCreateChatbot} className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-2 text-brown-800">Name</label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  className="w-full px-4 py-2 border border-brown-300 rounded-lg focus:ring-2 focus:ring-brown-500 focus:border-transparent bg-white text-brown-900"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-medium mb-2 text-brown-800">Description</label>
                <textarea
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  className="w-full px-4 py-2 border border-brown-300 rounded-lg focus:ring-2 focus:ring-brown-500 focus:border-transparent bg-white text-brown-900"
                  rows={3}
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-medium mb-2 text-brown-800">Website URL</label>
                <input
                  type="url"
                  value={formData.websiteUrl}
                  onChange={(e) => setFormData({ ...formData, websiteUrl: e.target.value })}
                  placeholder="https://example.com"
                  className="w-full px-4 py-2 border border-brown-300 rounded-lg focus:ring-2 focus:ring-brown-500 focus:border-transparent bg-white text-brown-900"
                  required
                />
                <p className="text-sm text-brown-600 mt-1">
                  The chatbot will analyze and learn from this website
                </p>
              </div>

              <button
                type="submit"
                disabled={creating}
                className="w-full px-6 py-3 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-xl font-medium disabled:opacity-50 hover:shadow-lg transition-all flex items-center justify-center gap-2"
              >
                <CheckCircle className="w-5 h-5" /> Create Chatbot
              </button>
            </form>
          </motion.div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {chatbots.map((chatbot) => (
            <motion.div
              key={chatbot.id}
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              className="bg-brown-50/90 backdrop-blur-sm rounded-2xl shadow-lg p-6 hover:shadow-xl transition-all border border-brown-200"
            >
              <div className="flex items-center gap-2 mb-3">
                <Book className="w-5 h-5 text-brown-700" />
                <h3 className="text-xl font-bold text-brown-800">{chatbot.name}</h3>
              </div>
              <p className="text-brown-700 mb-4">{chatbot.description}</p>

              <div className="space-y-2">
                <Link
                  href={`/chatbot/${chatbot.id}`}
                  className="flex items-center justify-center gap-2 w-full px-4 py-2 bg-brown-100 text-brown-800 rounded-lg hover:bg-brown-200 transition-colors font-medium"
                >
                  <Eye className="w-4 h-4" />
                  Preview Chatbot
                </Link>

                <button
                  onClick={() => {
                    handleGetEmbedCode(chatbot.id);
                    setSelectedChatbot(chatbot);
                  }}
                  className="flex items-center justify-center gap-2 w-full px-4 py-2 bg-gold-100 text-gold-800 rounded-lg hover:bg-gold-200 transition-colors font-medium"
                >
                  <Code className="w-4 h-4" />
                  Get Embed Code
                </button>
              </div>
            </motion.div>
          ))}
        </div>

        {chatbots.length === 0 && !showCreateForm && (
          <div className="text-center py-16">
            <Book className="w-20 h-20 text-brown-400 mx-auto mb-4" strokeWidth={1.5} />
            <p className="text-xl text-brown-700 mb-4">No chatbots yet</p>
            <button
              onClick={() => setShowCreateForm(true)}
              className="px-6 py-3 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-xl font-medium hover:shadow-lg transition-all inline-flex items-center gap-2"
            >
              <Plus className="w-5 h-5" />
              Create Your First Chatbot
            </button>
          </div>
        )}

        {embedCode && selectedChatbot && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="fixed inset-0 bg-black/50 backdrop-blur-sm flex items-center justify-center p-4 z-50"
            onClick={() => setEmbedCode('')}
          >
            <div
              className="bg-brown-50 rounded-2xl p-8 max-w-2xl w-full border-2 border-brown-300 shadow-2xl"
              onClick={(e) => e.stopPropagation()}
            >
              <div className="flex items-center gap-2 mb-4">
                <Code className="w-6 h-6 text-brown-700" />
                <h3 className="text-2xl font-bold text-brown-800">Embed Code for {selectedChatbot.name}</h3>
              </div>
              <pre className="bg-brown-100 p-4 rounded-lg overflow-x-auto mb-4 border border-brown-300 text-brown-900">
                <code>{embedCode}</code>
              </pre>
              <div className="flex gap-4">
                <button
                  onClick={() => {
                    navigator.clipboard.writeText(embedCode);
                    alert('Copied to clipboard!');
                  }}
                  className="flex-1 px-4 py-2 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-lg hover:shadow-lg transition-all flex items-center justify-center gap-2"
                >
                  <Copy className="w-4 h-4" />
                  Copy Code
                </button>
                <button
                  onClick={() => setEmbedCode('')}
                  className="px-4 py-2 bg-brown-200 text-brown-800 rounded-lg hover:bg-brown-300 transition-colors flex items-center gap-2"
                >
                  <X className="w-4 h-4" />
                  Close
                </button>
              </div>
            </div>
          </motion.div>
        )}
      </div>
    </main>
  );
}
