'use client';

import { motion } from 'framer-motion';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { Book, Sparkles, Zap, Heart } from 'lucide-react';

export default function Home() {
  const router = useRouter();

  return (
    <main className="relative min-h-screen overflow-hidden">{/* Background handled by globals.css */}

      <div className="relative z-10 flex flex-col items-center justify-center min-h-screen p-4">
        <motion.div
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          transition={{ duration: 0.6, ease: 'easeOut' }}
          className="text-center max-w-4xl"
        >
          <div className="flex items-center justify-center gap-3 mb-6">
            <Book className="w-16 h-16 text-brown-700" strokeWidth={1.5} />
            <h1 className="text-6xl md:text-7xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-brown-700 via-brown-600 to-gold-700">
              Prayer-Chat
            </h1>
          </div>
          <p className="text-xl md:text-2xl text-brown-800 mb-4 font-semibold">
            Christian AI Chatbots with Biblical Wisdom
          </p>
          <p className="text-lg text-brown-700 mb-12 max-w-2xl mx-auto leading-relaxed">
            Transform your ministry or business website into an intelligent AI assistant rooted in Christian values. Provide a URL and let Prayer-Chat create a chatbot that shares wisdom with grace.
          </p>

          <motion.div
            initial={{ opacity: 0, scale: 0.95 }}
            animate={{ opacity: 1, scale: 1 }}
            transition={{ duration: 0.6, delay: 0.2 }}
            className="flex flex-col sm:flex-row gap-4 justify-center items-center"
          >
            <button
              onClick={() => router.push('/login')}
              className="px-8 py-4 bg-gradient-to-r from-brown-600 to-gold-600 text-white rounded-xl font-semibold text-lg hover:shadow-2xl hover:scale-105 transition-all flex items-center gap-2"
            >
              <Book className="w-5 h-5" />
              Get Started
            </button>
            <Link
              href="/pricing"
              className="px-8 py-4 bg-brown-50 text-brown-800 rounded-xl font-semibold text-lg border-2 border-brown-300 hover:border-brown-600 hover:shadow-lg transition-all"
            >
              View Pricing
            </Link>
          </motion.div>

          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.6, delay: 0.4 }}
            className="mt-16 grid grid-cols-1 md:grid-cols-3 gap-8 text-left"
          >
            <div className="bg-brown-50/80 backdrop-blur-sm p-6 rounded-2xl border border-brown-200 shadow-lg hover:shadow-xl transition-shadow">
              <div className="flex justify-center mb-3">
                <Zap className="w-12 h-12 text-gold-600" strokeWidth={1.5} />
              </div>
              <h3 className="text-xl font-bold mb-2 text-brown-800">Quick Setup</h3>
              <p className="text-brown-700">
                Just enter your website URL and we'll analyze it to create your faith-based chatbot
              </p>
            </div>
            <div className="bg-brown-50/80 backdrop-blur-sm p-6 rounded-2xl border border-brown-200 shadow-lg hover:shadow-xl transition-shadow">
              <div className="flex justify-center mb-3">
                <Book className="w-12 h-12 text-brown-700" strokeWidth={1.5} />
              </div>
              <h3 className="text-xl font-bold mb-2 text-brown-800">Biblical Wisdom</h3>
              <p className="text-brown-700">
                Infused with Christian values and optional Bible verses to guide conversations with grace
              </p>
            </div>
            <div className="bg-brown-50/80 backdrop-blur-sm p-6 rounded-2xl border border-brown-200 shadow-lg hover:shadow-xl transition-shadow">
              <div className="flex justify-center mb-3">
                <Heart className="w-12 h-12 text-gold-600" strokeWidth={1.5} />
              </div>
              <h3 className="text-xl font-bold mb-2 text-brown-800">Ministry Focused</h3>
              <p className="text-brown-700">
                Perfect for churches, ministries, and Christian businesses to serve with compassion
              </p>
            </div>
          </motion.div>
        </motion.div>
      </div>
    </main>
  );
}
