'use client';

import { motion } from 'framer-motion';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import WaveBackground from '@/components/WaveBackground';

export default function Home() {
  const router = useRouter();

  return (
    <main className="relative min-h-screen overflow-hidden bg-gradient-to-br from-blue-50 via-white to-purple-50">
      <WaveBackground />

      <div className="relative z-10 flex flex-col items-center justify-center min-h-screen p-4">
        <motion.div
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, ease: 'easeOut' }}
          className="text-center max-w-4xl"
        >
          <h1 className="text-6xl md:text-7xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-blue-600 to-purple-600 mb-6">
            TjanaBot
          </h1>
          <p className="text-xl md:text-2xl text-gray-700 mb-4">
            Create AI chatbots from your website in minutes
          </p>
          <p className="text-lg text-gray-600 mb-12 max-w-2xl mx-auto">
            Transform your website into an intelligent AI assistant. Just provide a URL and let TjanaBot analyze your content to create a customized chatbot.
          </p>

          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.6, delay: 0.2 }}
            className="flex flex-col sm:flex-row gap-4 justify-center items-center"
          >
            <button
              onClick={() => router.push('/dashboard')}
              className="px-8 py-4 bg-gradient-to-r from-blue-500 to-purple-600 text-white rounded-xl font-semibold text-lg hover:shadow-2xl hover:scale-105 transition-all"
            >
              Create Your First Chatbot
            </button>
            <Link
              href="/dashboard"
              className="px-8 py-4 bg-white text-gray-700 rounded-xl font-semibold text-lg border-2 border-gray-300 hover:border-blue-500 hover:shadow-lg transition-all"
            >
              View Dashboard
            </Link>
          </motion.div>

          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.6, delay: 0.4 }}
            className="mt-16 grid grid-cols-1 md:grid-cols-3 gap-8 text-left"
          >
            <div className="bg-white/60 backdrop-blur-sm p-6 rounded-2xl border border-gray-200">
              <div className="text-4xl mb-3">🚀</div>
              <h3 className="text-xl font-bold mb-2 text-gray-800">Quick Setup</h3>
              <p className="text-gray-600">
                Just enter your website URL and we'll analyze it to create your chatbot
              </p>
            </div>
            <div className="bg-white/60 backdrop-blur-sm p-6 rounded-2xl border border-gray-200">
              <div className="text-4xl mb-3">🤖</div>
              <h3 className="text-xl font-bold mb-2 text-gray-800">AI-Powered</h3>
              <p className="text-gray-600">
                Powered by advanced AI to understand your content and answer questions
              </p>
            </div>
            <div className="bg-white/60 backdrop-blur-sm p-6 rounded-2xl border border-gray-200">
              <div className="text-4xl mb-3">🎨</div>
              <h3 className="text-xl font-bold mb-2 text-gray-800">Easy Integration</h3>
              <p className="text-gray-600">
                Get embed code and add your chatbot to any website instantly
              </p>
            </div>
          </motion.div>
        </motion.div>
      </div>
    </main>
  );
}
