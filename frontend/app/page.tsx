'use client';

import { motion } from 'framer-motion';
import Link from 'next/link';
import ChatInterface from '@/components/ChatInterface';
import WaveBackground from '@/components/WaveBackground';

export default function Home() {
  return (
    <main className="relative min-h-screen overflow-hidden bg-gradient-to-br from-blue-50 via-white to-purple-50">
      <WaveBackground />

      <div className="relative z-10 flex flex-col items-center justify-center min-h-screen p-4">
        <div className="absolute top-8 right-8">
          <Link
            href="/dashboard"
            className="px-6 py-3 bg-gradient-to-r from-blue-500 to-purple-600 text-white rounded-xl font-medium hover:shadow-lg transition-all"
          >
            Dashboard
          </Link>
        </div>

        <motion.div
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, ease: 'easeOut' }}
          className="text-center mb-8"
        >
          <h1 className="text-5xl md:text-6xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-blue-600 to-purple-600 mb-4">
            TjanaBot
          </h1>
          <p className="text-gray-600 text-lg">
            Create AI chatbots from your website
          </p>
        </motion.div>

        <motion.div
          initial={{ opacity: 0, scale: 0.95 }}
          animate={{ opacity: 1, scale: 1 }}
          transition={{ duration: 0.6, delay: 0.2, ease: 'easeOut' }}
          className="w-full max-w-4xl"
        >
          <ChatInterface />
        </motion.div>

        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.6, delay: 0.4 }}
          className="mt-8 text-center"
        >
          <Link
            href="/dashboard"
            className="text-blue-600 hover:text-purple-600 font-medium underline"
          >
            Go to Dashboard to create your first chatbot
          </Link>
        </motion.div>
      </div>
    </main>
  );
}
