'use client';

import { useState, useEffect } from 'react';
import { motion } from 'framer-motion';
import { getAllChatbots, createChatbot, analyzeWebsite, getEmbedCode, type Chatbot } from '@/lib/api';
import Link from 'next/link';

export default function Dashboard() {
  const [chatbots, setChatbots] = useState<Chatbot[]>([]);
  const [loading, setLoading] = useState(true);
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
    } catch (error) {
      console.error('Error loading chatbots:', error);
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
        handleAnalyzeWebsite(newChatbot.chatbotId, formData.websiteUrl);
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
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-xl">Loading...</div>
      </div>
    );
  }

  return (
    <main className="min-h-screen bg-gradient-to-br from-blue-50 via-white to-purple-50 p-8">
      <div className="max-w-7xl mx-auto">
        <div className="flex justify-between items-center mb-8">
          <h1 className="text-4xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-blue-600 to-purple-600">
            TjanaBot Dashboard
          </h1>
          <button
            onClick={() => setShowCreateForm(!showCreateForm)}
            className="px-6 py-3 bg-gradient-to-r from-blue-500 to-purple-600 text-white rounded-xl font-medium hover:shadow-lg transition-all"
          >
            {showCreateForm ? 'Cancel' : 'Create New Chatbot'}
          </button>
        </div>

        {showCreateForm && (
          <motion.div
            initial={{ opacity: 0, y: -20 }}
            animate={{ opacity: 1, y: 0 }}
            className="bg-white rounded-2xl shadow-xl p-8 mb-8"
          >
            <h2 className="text-2xl font-bold mb-6">Create New Chatbot</h2>
            <form onSubmit={handleCreateChatbot} className="space-y-4">
              <div>
                <label className="block text-sm font-medium mb-2">Name</label>
                <input
                  type="text"
                  value={formData.name}
                  onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                  className="w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 text-gray-900"
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-medium mb-2">Description</label>
                <textarea
                  value={formData.description}
                  onChange={(e) => setFormData({ ...formData, description: e.target.value })}
                  className="w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 text-gray-900"
                  rows={3}
                  required
                />
              </div>

              <div>
                <label className="block text-sm font-medium mb-2">Website URL</label>
                <input
                  type="url"
                  value={formData.websiteUrl}
                  onChange={(e) => setFormData({ ...formData, websiteUrl: e.target.value })}
                  placeholder="https://example.com"
                  className="w-full px-4 py-2 border rounded-lg focus:ring-2 focus:ring-blue-500 text-gray-900"
                  required
                />
                <p className="text-sm text-gray-500 mt-1">
                  The chatbot will analyze and learn from this website
                </p>
              </div>

              <button
                type="submit"
                disabled={creating}
                className="w-full px-6 py-3 bg-gradient-to-r from-blue-500 to-purple-600 text-white rounded-xl font-medium disabled:opacity-50"
              >
                {creating ? 'Creating...' : 'Create Chatbot'}
              </button>
            </form>
          </motion.div>
        )}

        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
          {chatbots.map((chatbot) => (
            <motion.div
              key={chatbot.chatbotId}
              initial={{ opacity: 0, scale: 0.9 }}
              animate={{ opacity: 1, scale: 1 }}
              className="bg-white rounded-2xl shadow-lg p-6 hover:shadow-xl transition-all"
            >
              <h3 className="text-xl font-bold mb-2">{chatbot.name}</h3>
              <p className="text-gray-600 mb-4">{chatbot.description}</p>

              <div className="space-y-2">
                <Link
                  href={`/chatbot/${chatbot.chatbotId}`}
                  className="block w-full px-4 py-2 bg-blue-100 text-blue-700 rounded-lg text-center hover:bg-blue-200 transition-colors"
                >
                  Preview Chatbot
                </Link>

                <button
                  onClick={() => {
                    handleGetEmbedCode(chatbot.chatbotId);
                    setSelectedChatbot(chatbot);
                  }}
                  className="w-full px-4 py-2 bg-purple-100 text-purple-700 rounded-lg hover:bg-purple-200 transition-colors"
                >
                  Get Embed Code
                </button>
              </div>
            </motion.div>
          ))}
        </div>

        {chatbots.length === 0 && !showCreateForm && (
          <div className="text-center py-16">
            <p className="text-xl text-gray-600 mb-4">No chatbots yet</p>
            <button
              onClick={() => setShowCreateForm(true)}
              className="px-6 py-3 bg-gradient-to-r from-blue-500 to-purple-600 text-white rounded-xl font-medium"
            >
              Create Your First Chatbot
            </button>
          </div>
        )}

        {embedCode && selectedChatbot && (
          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            className="fixed inset-0 bg-black/50 flex items-center justify-center p-4 z-50"
            onClick={() => setEmbedCode('')}
          >
            <div
              className="bg-white rounded-2xl p-8 max-w-2xl w-full"
              onClick={(e) => e.stopPropagation()}
            >
              <h3 className="text-2xl font-bold mb-4">Embed Code for {selectedChatbot.name}</h3>
              <pre className="bg-gray-100 p-4 rounded-lg overflow-x-auto mb-4">
                <code>{embedCode}</code>
              </pre>
              <div className="flex gap-4">
                <button
                  onClick={() => {
                    navigator.clipboard.writeText(embedCode);
                    alert('Copied to clipboard!');
                  }}
                  className="flex-1 px-4 py-2 bg-blue-500 text-white rounded-lg hover:bg-blue-600"
                >
                  Copy Code
                </button>
                <button
                  onClick={() => setEmbedCode('')}
                  className="px-4 py-2 bg-gray-200 text-gray-700 rounded-lg hover:bg-gray-300"
                >
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
